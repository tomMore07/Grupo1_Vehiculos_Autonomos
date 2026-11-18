import unittest
from itertools import count, islice
import inspect
import json
import pipeline
from pipeline import Anomaly, anomaly_to_json, Reading, curry, detect_anomalies, mean_var, moving_average, sliding_windows


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

    def test_anomaly_json_contract(self):
        d = json.loads(anomaly_to_json(Anomaly(97, 13.48, 24.96, -32.17)))
        self.assertEqual(set(d), {"t", "value", "mean", "zscore"})
        self.assertEqual(d["t"], 97)

    def test_windows_are_immutable_tuples(self):
        for w in islice(sliding_windows(3, count()), 5):
            self.assertIsInstance(w, tuple)

    def test_window_does_not_consume_more_than_needed(self):
        consumed = []
        def fuente():
            for i in count():
                consumed.append(i)       # solo para observar la pereza en la prueba
                yield float(i)
        next(sliding_windows(3, fuente()))
        self.assertEqual(consumed, [0, 1, 2])

    def test_no_mutable_structures_in_core(self):
        src = inspect.getsource(pipeline.sliding_windows) + inspect.getsource(pipeline.detect_anomalies)
        for prohibido in ("deque", ".append(", ".pop(", "[:] ="):
            self.assertNotIn(prohibido, src)


if __name__ == "__main__":
    unittest.main()
