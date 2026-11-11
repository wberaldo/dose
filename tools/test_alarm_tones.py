import array
import hashlib
from pathlib import Path
import unittest
import wave

ROOT = Path(__file__).resolve().parents[1] / 'app/src/main/res/raw'


class AlarmToneAssetsTest(unittest.TestCase):
    def test_seven_distinct_playable_pcm_files(self):
        hashes = set()
        for name in ['alarm', 'suave', 'gotas', 'sinos', 'pulso', 'digital', 'classico']:
            with self.subTest(tone=name):
                with wave.open(str(ROOT / ('dose_' + name + '.wav')), 'rb') as wav:
                    self.assertEqual((wav.getnchannels(), wav.getsampwidth(), wav.getcomptype()), (1, 2, 'NONE'))
                    self.assertGreaterEqual(wav.getframerate(), 16000)
                    duration = wav.getnframes() / wav.getframerate()
                    self.assertGreater(duration, 1)
                    self.assertLess(duration, 6)
                    pcm = wav.readframes(wav.getnframes())
                samples = array.array('h', pcm)
                self.assertGreater(max(abs(v) for v in samples), 2000)
                self.assertLess(max(abs(v) for v in samples), 32767)
                self.assertLess(abs(samples[0]), 20)
                self.assertLess(abs(samples[-1]), 20)
                hashes.add(hashlib.sha256(pcm).hexdigest())
        self.assertEqual(len(hashes), 7)


if __name__ == '__main__':
    unittest.main()
