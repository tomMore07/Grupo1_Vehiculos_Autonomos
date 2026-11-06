import unittest
from itertools import count, islice
from pipeline import Reading, curry, detect_anomalies, mean_var, moving_average, sliding_windows


class TestPipeline(unittest.TestCase):
    def test_mean_var(self):
        self.assertEqual(mean_var((1.0, 2.0, 3.0)), (2.0, 2 / 3))

    def test_windows_lazy_infinite(self):
        self.assertEqual(list(islice(sliding_windows(3, count()), 2)), [(0, 1, 2), (1, 2, 3)])

    def test_moving_average_curried(self):
        self.assertEqual(list(islice(moving_average(2)(count()), 3)), [0.5, 1.5, 2.5])
        self.assertEqual(list(moving_average(2, [1.0, 3.0, 5.0])), [2.0, 4.0])

    def test_curry(self):
        add3 = curry(lambda a, b, c: a + b + c)
        self.assertEqual(add3(1)(2)(3), add3(1, 2)(3))

    def test_anomaly_detected(self):
        data = [Reading(i, 10.0 + (i % 2) * 0.1) for i in range(30)] + [Reading(30, 50.0)]
        res = list(detect_anomalies(3.0)(10)(data))
        self.assertEqual([a.t for a in res], [30])

    def test_purity_same_input_same_output(self):
        data = [Reading(i, float(i % 5)) for i in range(40)]
        self.assertEqual(list(detect_anomalies(1.0)(5)(data)), list(detect_anomalies(1.0)(5)(data)))

    def test_input_not_mutated(self):
        w = (1.0, 2.0, 3.0)
        mean_var(w)
        self.assertEqual(w, (1.0, 2.0, 3.0))


if __name__ == "__main__":
    unittest.main()
