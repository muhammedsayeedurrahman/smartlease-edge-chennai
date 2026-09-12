# -*- coding: utf-8 -*-
"""
PILOT PROBE — acoustic. Checks two pilot recordings before an hour of capture is spent.

Ten checks, in three groups (PILOT_CHECKLIST.md §A):

  FORMAT      is the file actually uncompressed 44.1 kHz mono PCM16, and - the check that
              matters most - does it still have energy above 8 kHz? A voice codec band-limits
              there, and a 16 kHz Opus file upsampled to 44.1 keeps a .wav extension, a
              correct header, and no content above 8 kHz. That is precisely the defect in the
              current training set and a header check will not find it.

  PROCESSING  did the handset apply AGC or noise suppression on the way in. AGC is visible as
              noise-floor drift between the silence before the first strike and the silence
              after the last; noise suppression is visible as a missing low-frequency room
              floor and as isolated narrow spikes ("musical noise") in a silent segment.

  SIGNAL      extract the 36 PRODUCTION features (ml/acoustic/android_features.py, the same
              code the shipped model was fitted with and AcousticFeatureExtractor.kt mirrors)
              from both files and report which of them separate hollow from solid on THIS
              handset. Two spots cannot predict accuracy; they can tell you in ten minutes
              whether the microphone resolves the difference at all.

Usage:
    python pilot_audio_probe.py <dir-or-zip-of-2-wavs>
    python pilot_audio_probe.py hollow.wav solid.wav
"""
import glob
import json
import os
import struct
import sys
import tempfile
import warnings
import zipfile

warnings.filterwarnings("ignore")

import numpy as np

HERE = os.path.abspath(os.path.dirname(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ACOUSTIC = os.path.join(ROOT, "smartlease-edge-chennai-master", "ml", "acoustic")

WANT_RATE = 44100
WANT_CHANNELS = 1
WANT_BITS = 16
WANT_SECONDS = 8.0

OK, WARN, FAIL = "PASS", "WARN", "FAIL"
_RESULTS = []


def say(status, name, detail):
    _RESULTS.append((status, name, detail))
    mark = {OK: "  ok  ", WARN: " WARN ", FAIL: " FAIL "}[status]
    print("[%s] %-34s %s" % (mark, name, detail))


# ---------------------------------------------------------------- container, by hand
def read_riff(path):
    """Parse the RIFF header directly. Do not ask a library whether the container is right -
    soundfile will happily decode an m4a and report 'it is audio'."""
    with open(path, "rb") as fh:
        head = fh.read(12)
        if len(head) < 12 or head[0:4] != b"RIFF" or head[8:12] != b"WAVE":
            return {"container": head[0:4].decode("latin1", "replace") if head else "empty",
                    "is_wave": False}
        info = {"container": "RIFF/WAVE", "is_wave": True, "chunks": []}
        while True:
            hdr = fh.read(8)
            if len(hdr) < 8:
                break
            cid, size = struct.unpack("<4sI", hdr)
            cid = cid.decode("latin1", "replace")
            info["chunks"].append((cid, size))
            body = fh.read(size + (size & 1))
            if cid == "fmt " and size >= 16:
                (fmt, ch, rate, byterate, align, bits) = struct.unpack("<HHIIHH", body[:16])
                info.update(fmt_tag=fmt, channels=ch, rate=rate, bits=bits,
                            byte_rate=byterate, block_align=align)
            elif cid == "data":
                info["data_bytes"] = size
        return info


FMT_NAMES = {1: "PCM (uncompressed)", 3: "IEEE float", 6: "A-law", 7: "mu-law",
             0x11: "IMA ADPCM", 0x55: "MPEG Layer 3", 0xFFFE: "WAVE_FORMAT_EXTENSIBLE"}


def check_container(path):
    r = read_riff(path)
    if not r.get("is_wave"):
        say(FAIL, "container", "not RIFF/WAVE - got %r. A codec has touched this file."
            % r.get("container"))
        return r
    fmt = r.get("fmt_tag")
    name = FMT_NAMES.get(fmt, "unknown tag %s" % fmt)
    say(OK if fmt in (1, 0xFFFE) else FAIL, "container / codec", "RIFF/WAVE, fmt %s = %s" % (fmt, name))
    say(OK if r.get("rate") == WANT_RATE else FAIL, "sample rate",
        "%s Hz (want %d)" % (r.get("rate"), WANT_RATE))
    say(OK if r.get("channels") == WANT_CHANNELS else FAIL, "channels",
        "%s (want %d)" % (r.get("channels"), WANT_CHANNELS))
    say(OK if r.get("bits") == WANT_BITS else FAIL, "bit depth",
        "%s-bit (want %d)" % (r.get("bits"), WANT_BITS))
    if r.get("data_bytes") and r.get("byte_rate"):
        secs = r["data_bytes"] / r["byte_rate"]
        st = OK if abs(secs - WANT_SECONDS) < 1.5 else WARN
        say(st, "duration", "%.2f s (expect ~%.0f)" % (secs, WANT_SECONDS))
    return r


# ---------------------------------------------------------------- spectrum-based checks
def longterm_spectrum(x, sr, nfft=4096):
    win = np.hanning(nfft)
    hop = nfft // 2
    n = 1 + max(0, (len(x) - nfft) // hop)
    if n == 0:
        return np.zeros(nfft // 2 + 1), np.linspace(0, sr / 2, nfft // 2 + 1)
    acc = np.zeros(nfft // 2 + 1)
    for i in range(n):
        acc += np.abs(np.fft.rfft(x[i * hop:i * hop + nfft] * win)) ** 2
    return acc / n, np.linspace(0, sr / 2, nfft // 2 + 1)


def db(v, ref=1.0):
    return 10.0 * np.log10(np.maximum(v, 1e-30) / ref)


def check_bandwidth(x, sr):
    """THE important one. A codec-processed file has nothing above ~8 kHz."""
    p, f = longterm_spectrum(x, sr)
    tot = p.sum() + 1e-30
    hi = p[f >= 8000].sum() / tot
    st = OK if hi > 1e-4 else FAIL
    say(st, "energy above 8 kHz",
        "%.4f%% of total (%.1f dB below full band)%s"
        % (100 * hi, db(hi), "" if st == OK else
           "  <-- BAND-LIMITED. This file has been through a voice codec, "
           "whatever its header says."))

    # A resampled file has a BRICKWALL - an abrupt cliff - not merely a quiet top end.
    # Natural roll-off is gradual, so look for a step of >30 dB across a narrow span that
    # then stays down. Quiet-but-continuous high end is normal and must not be flagged.
    pdb = db(p / (p.max() + 1e-30))
    k = max(3, int(200.0 / (f[1] - f[0])))                 # ~200 Hz smoothing
    sm = np.convolve(pdb, np.ones(k) / k, mode="same")
    span = max(2, int(500.0 / (f[1] - f[0])))              # cliff measured over ~500 Hz
    lo_i, hi_i = int(0.10 * len(f)), int(0.92 * len(f))
    drop, edge = 0.0, 0.0
    for i in range(lo_i, hi_i - span):
        d_ = sm[i] - sm[i + span]
        if d_ > drop and sm[i + span:].max() < sm[i] - 20.0:
            drop, edge = d_, f[i]
    st = OK if drop < 30.0 else WARN
    say(st, "resampling brickwall",
        ("none detected (high end rolls off smoothly)" if st == OK else
         "%.0f dB cliff at %.0f Hz  <-- recorded at a lower rate and resampled up"
         % (drop, edge)))
    return hi, edge


# ---------------------------------------------------------------- transients
def find_strikes(x, sr, thresh=0.35, refractory_s=0.25):
    env = np.abs(x)
    w = int(0.005 * sr)
    env = np.convolve(env, np.ones(w) / w, mode="same")
    env /= (env.max() + 1e-12)
    idx, last = [], -10 ** 9
    for i in np.where(env > thresh)[0]:
        if i - last > refractory_s * sr:
            local = int(np.argmax(np.abs(x[i:i + int(0.02 * sr)]))) + i
            idx.append(local)
            last = i
    return idx, env


def dbfs(v, full_scale=32768.0):
    """Relative to int16 full scale - the same reference the in-app peak meter uses."""
    return 20.0 * np.log10(max(abs(float(v)) / full_scale, 1e-9))


def check_strikes(x, sr):
    idx, env = find_strikes(x, sr)
    n = len(idx)
    st = OK if 4 <= n <= 7 else (WARN if 2 <= n <= 9 else FAIL)
    peaks = [dbfs(x[i]) for i in idx]
    gaps = [(idx[i + 1] - idx[i]) / sr for i in range(n - 1)]
    say(st, "strikes detected", "%d  peaks %s dBFS"
        % (n, "[" + ", ".join("%.1f" % p for p in peaks) + "]"))
    if gaps:
        st = OK if min(gaps) > 0.45 else WARN
        say(st, "gaps between strikes",
            "min %.2f s, max %.2f s%s" % (min(gaps), max(gaps),
                                          "" if st == OK else "  <-- too fast, tails overlap"))
    if peaks:
        lo, hi = min(peaks), max(peaks)
        st = OK if (-30.0 < lo and hi < -1.5) else WARN
        say(st, "strike level", "%.1f to %.1f dBFS (want -30 .. -1.5)" % (lo, hi))
        st = OK if (hi - lo) < 12 else WARN
        say(st, "strike consistency", "%.1f dB spread across strikes" % (hi - lo))
    return idx, env


# ---------------------------------------------------------------- AGC / NS
def check_agc(x, sr, idx):
    """
    AGC rides the gain back up after a transient. Two independent probes:

      RECOVERY  for each strike, the floor 400 ms after the peak (gain still ducked) against
                the floor just before the next strike (gain recovered). A compressor makes
                the later window LOUDER. This is the sensitive one - sampling only the quiet
                just before each strike misses AGC entirely, because that is exactly where
                the gain has already finished recovering.

      DRIFT     silence before the first strike vs at the very end. Catches slow riding.

    It matters because feature 32 is decay-time-to-10%, and a compressor that lifts the tail
    lengthens it - so AGC does not merely add noise, it corrupts the single most physically
    meaningful feature in the vector.
    """
    def rms_db(seg):
        if seg.size < 256:
            return None
        r = float(np.sqrt(np.mean(seg.astype(np.float64) ** 2))) / 32768.0
        return 20.0 * np.log10(r + 1e-12)

    rises = []
    for i in range(len(idx) - 1):
        a, b = idx[i], idx[i + 1]
        if b - a < int(0.9 * sr):
            continue
        early = rms_db(x[a + int(0.40 * sr): a + int(0.60 * sr)])
        late = rms_db(x[b - int(0.25 * sr): b - int(0.05 * sr)])
        if early is not None and late is not None:
            rises.append(late - early)
    if rises:
        med = float(np.median(rises))
        st = OK if med < 4.0 else (WARN if med < 8.0 else FAIL)
        say(st, "AGC (post-strike recovery)",
            "floor rises %.1f dB between 0.5 s and 1.5 s after a strike (median of %d)%s"
            % (med, len(rises), "" if st == OK else
               "  <-- gain is recovering; the decay-time feature is corrupted"))
    else:
        say(WARN, "AGC (post-strike recovery)", "not enough spacing between strikes to judge")

    w = int(0.30 * sr)
    if not idx or idx[0] < w or len(x) - idx[-1] < 2 * w:
        say(WARN, "AGC (long-term drift)", "not enough silence around the strikes to judge")
        return None
    vals = {"pre-first": rms_db(x[max(0, idx[0] - w - int(0.05 * sr)): idx[0] - int(0.05 * sr)]),
            "end": rms_db(x[-w:])}
    vals = {k: v for k, v in vals.items() if v is not None}
    drift = max(vals.values()) - min(vals.values())
    st = OK if drift < 6.0 else (WARN if drift < 10.0 else FAIL)
    say(st, "AGC (long-term drift)",
        "%s  -> %.1f dB spread" % (", ".join("%s %.1f dBFS" % (k, v) for k, v in vals.items()),
                                   drift))
    return drift


def check_ns(x, sr, idx):
    """Noise suppression removes the room. A real room has broadband rumble below 200 Hz and
    mains harmonics; silence that is *too* clean down there has been processed."""
    w = int(0.30 * sr)
    seg = x[:w] if not idx or idx[0] < w else x[max(0, idx[0] - w - int(0.05 * sr)):
                                                idx[0] - int(0.05 * sr)]
    if seg.size < 1024:
        say(WARN, "noise suppression", "no usable silent segment")
        return
    p, f = longterm_spectrum(seg.astype(np.float64), sr, nfft=2048)
    tot = p.sum() + 1e-30
    lf = p[f < 200].sum() / tot
    st = OK if lf > 0.02 else WARN
    say(st, "room floor below 200 Hz",
        "%.2f%% of silent-segment energy%s"
        % (100 * lf, "" if st == OK else
           "  <-- the low-frequency room is missing; a high-pass or NS is active"))

    # musical noise: a processed silence is spiky, an unprocessed one is smooth.
    # Restricted to >300 Hz: 50 Hz mains and its low harmonics are narrow peaks that a real
    # room genuinely has, and counting them would flag every honest recording in India.
    band = f > 300.0
    pn = p[band] / (p[band].mean() + 1e-30)
    spike = float(np.percentile(pn, 99.5) / (np.median(pn) + 1e-12))
    st = OK if spike < 60 else WARN
    say(st, "musical noise in silence",
        "p99.5/median = %.0f%s" % (spike, "" if st == OK else
                                   "  <-- isolated narrow peaks, typical of spectral NS"))


def check_levels(x):
    xi = np.asarray(x)
    clip = int(np.sum(np.abs(xi) >= 32767))
    st = OK if clip == 0 else (WARN if clip < 20 else FAIL)
    say(st, "clipping", "%d samples at full scale" % clip)
    dc = float(np.mean(xi)) / 32768.0
    st = OK if abs(dc) < 0.01 else WARN
    say(st, "DC offset", "%.5f of full scale" % dc)


# ---------------------------------------------------------------- production features
def production_features(paths_by_label):
    sys.path.insert(0, ACOUSTIC)
    cwd = os.getcwd()
    try:
        os.chdir(ACOUSTIC)
        import android_features as af
        from train_tap_classifier import segment_taps
    except Exception as e:
        print("\n  (feature comparison skipped: %s: %s)" % (type(e).__name__, e))
        return None
    finally:
        os.chdir(cwd)

    names = (["mfcc%d_mean" % i for i in range(13)] + ["mfcc%d_std" % i for i in range(13)] +
             ["centroid_mean", "centroid_std", "rolloff85", "bandwidth", "flatness",
              "zcr", "decay_ms", "low<500", "mid500-2k", "high>=2k"])
    feats = {}
    for label, p in paths_by_label.items():
        clips = segment_taps(p)
        if not clips:
            print("  %s: no taps segmented - the production onset detector found nothing" % label)
            return None
        feats[label] = np.array([af.extract(c) for c in clips])
        print("  %-8s %d taps segmented by the production detector" % (label, len(clips)))
    if len(feats) != 2:
        return None

    (la, A), (lb, B) = list(feats.items())
    pooled = np.sqrt((A.std(0) ** 2 + B.std(0) ** 2) / 2.0) + 1e-9
    d = (A.mean(0) - B.mean(0)) / pooled
    order = np.argsort(-np.abs(d))
    print("\n  Largest separations between %s and %s (Cohen's d, production features):" % (la, lb))
    print("    %-14s %10s %10s %8s" % ("feature", la, lb, "d"))
    for i in order[:8]:
        print("    %-14s %10.3f %10.3f %8.2f" % (names[i], A.mean(0)[i], B.mean(0)[i], d[i]))
    big = int(np.sum(np.abs(d) > 1.0))
    st = OK if big >= 3 else (WARN if big >= 1 else FAIL)
    say(st, "hollow/solid separability", "%d of 36 features separate at |d| > 1.0" % big)
    return {"n_features_separating": big,
            "top": [{"feature": names[i], la: float(A.mean(0)[i]),
                     lb: float(B.mean(0)[i]), "d": float(d[i])} for i in order[:8]]}


# ---------------------------------------------------------------- driver
def collect(args):
    paths = []
    tmp = None
    for a in args:
        if os.path.isdir(a):
            paths += sorted(glob.glob(os.path.join(a, "**", "*.wav"), recursive=True))
            paths += sorted(glob.glob(os.path.join(a, "**", "*.*"), recursive=True))
        elif zipfile.is_zipfile(a):
            tmp = tempfile.mkdtemp(prefix="slx_pilot_")
            with zipfile.ZipFile(a) as z:
                z.extractall(tmp)
            paths += sorted(glob.glob(os.path.join(tmp, "**", "*.*"), recursive=True))
        else:
            paths.append(a)
    seen, out = set(), []
    for p in paths:
        rp = os.path.realpath(p)
        if rp in seen or os.path.isdir(rp):
            continue
        if os.path.splitext(rp)[1].lower() in (".wav", ".m4a", ".3gp", ".ogg", ".opus",
                                               ".mp3", ".aac", ".amr", ".flac"):
            seen.add(rp)
            out.append(rp)
    return out


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    files = collect(sys.argv[1:])
    if not files:
        print("no audio files found in", sys.argv[1:])
        return 2

    print("=" * 78)
    print("ACOUSTIC PILOT PROBE - %d file(s)" % len(files))
    print("=" * 78)

    import soundfile as sf
    labelled, report = {}, {}
    for path in files:
        base = os.path.basename(path)
        print("\n--- %s" % base)
        r = check_container(path)
        try:
            data, sr = sf.read(path, dtype="int16", always_2d=True)
        except Exception as e:
            say(FAIL, "decode", "%s: %s" % (type(e).__name__, e))
            continue
        x = data[:, 0].astype(np.float64)
        if data.shape[1] > 1:
            say(FAIL, "channels (decoded)", "%d channels present" % data.shape[1])
        check_levels(x)
        check_bandwidth(x, sr)
        idx, _ = check_strikes(x, sr)
        check_agc(x, sr, idx)
        check_ns(x, sr, idx)
        report[base] = {"riff": {k: v for k, v in r.items() if k != "chunks"},
                        "decoded_rate": int(sr), "n_strikes": len(idx)}
        low = base.lower()
        if "hollow" in low and "hollow" not in labelled:
            labelled["hollow"] = path
        elif "solid" in low and "solid" not in labelled:
            labelled["solid"] = path

    print("\n" + "=" * 78)
    print("SIGNAL - 36 production features")
    print("=" * 78)
    if len(labelled) == 2:
        sep = production_features(labelled)
        if sep:
            report["separability"] = sep
    else:
        print("  need one file with 'hollow' in the name and one with 'solid'.")
        print("  got:", ", ".join(os.path.basename(f) for f in files))

    print("\n" + "=" * 78)
    fails = [r for r in _RESULTS if r[0] == FAIL]
    warns = [r for r in _RESULTS if r[0] == WARN]
    if fails:
        print("VERDICT: DO NOT START THE BULK RUN - %d hard failure(s)" % len(fails))
        for _, n, d in fails:
            print("   FAIL  %-30s %s" % (n, d))
    elif warns:
        print("VERDICT: GO, with %d caveat(s) worth fixing first" % len(warns))
        for _, n, d in warns:
            print("   WARN  %-30s %s" % (n, d))
    else:
        print("VERDICT: GO - format, processing and technique all clean.")
    print("=" * 78)

    out = os.path.join(ROOT, "handoff", "runs", "pilot_audio_probe.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    report["verdict"] = "FAIL" if fails else ("GO_WITH_CAVEATS" if warns else "GO")
    report["checks"] = [{"status": s, "check": n, "detail": d} for s, n, d in _RESULTS]
    json.dump(report, open(out, "w", encoding="utf-8"), indent=2)
    print("wrote", out)
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
