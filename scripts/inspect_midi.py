from pathlib import Path
from collections import Counter

path = Path(r"e:\file\music coach\MID\Call of Silence.mid")
data = path.read_bytes()
fmt = int.from_bytes(data[8:10], "big")
ntr = int.from_bytes(data[10:12], "big")
div = int.from_bytes(data[12:14], "big")
print("fmt", fmt, "tracks", ntr, "ppq", div)


def vlq(buf, i):
    v = 0
    while True:
        b = buf[i]
        i += 1
        v = (v << 7) | (b & 0x7F)
        if b & 0x80 == 0:
            break
    return v, i


notes = []
i = 14
while i + 8 < len(data):
    if data[i : i + 4] == b"MTrk":
        ln = int.from_bytes(data[i + 4 : i + 8], "big")
        start = i + 8
        end = start + ln
        i = end
        t = 0
        rs = -1
        j = start
        active = {}
        while j < end:
            dt, j = vlq(data, j)
            t += dt
            if j >= end:
                break
            st = data[j]
            if st < 0x80:
                st = rs
            else:
                rs = st
                j += 1
            mt = st & 0xF0
            if mt in (0x90, 0x80):
                n = data[j]
                v = data[j + 1]
                j += 2
                if mt == 0x90 and v > 0:
                    active.setdefault(n, []).append((t, v))
                else:
                    if n in active and active[n]:
                        stt, vel = active[n].pop(0)
                        notes.append((stt, max(1, t - stt), n, vel))
            elif mt in (0xA0, 0xB0, 0xE0):
                j += 2
            elif mt in (0xC0, 0xD0):
                j += 1
            elif st == 0xFF:
                j += 1
                ln2, j = vlq(data, j)
                j += ln2
                rs = -1
            elif st in (0xF0, 0xF7):
                ln2, j = vlq(data, j)
                j += ln2
                rs = -1
            else:
                j += 1
    else:
        i += 1

print("notes", len(notes))
starts = sorted(set(n[0] for n in notes))
c = Counter(sum(1 for n in notes if n[0] == s) for s in starts)
print("unique starts", len(starts))
print("chord size hist", dict(sorted(c.items())))
print("sample", [(s, [n[2] for n in notes if n[0] == s]) for s in starts[:8]])
print("pitch", min(n[2] for n in notes), max(n[2] for n in notes))
