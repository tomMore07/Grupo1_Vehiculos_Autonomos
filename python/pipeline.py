"""Módulo 3 (Funcional): pipeline puro para telemetría de sensores.

Núcleo funcional (puro, sin I/O): moving_average, detect_anomalies, curry.
Cascarón imperativo (efectos): source_stream y main() al final.
"""
from __future__ import annotations

import random
from collections import deque
from dataclasses import dataclass
from functools import reduce, wraps
from itertools import islice
from typing import Callable, Iterable, Iterator, Tuple


# ---------- Currificación ----------
def curry(fn: Callable) -> Callable:
    """Currifica una función de aridad fija: f(a)(b)(c) == f(a, b, c)."""
    arity = fn.__code__.co_argcount

    @wraps(fn)
    def curried(*args):
        if len(args) >= arity:
            return fn(*args)
        return lambda *more: curried(*args, *more)

    return curried


# ---------- Tipos inmutables ----------
@dataclass(frozen=True)
class Reading:
    t: int
    value: float


@dataclass(frozen=True)
class Anomaly:
    t: int
    value: float
    mean: float
    zscore: float


# ---------- Funciones puras ----------
def mean_var(window: Tuple[float, ...]) -> Tuple[float, float]:
    """Media y varianza poblacional con reduce (sin mutación)."""
    n = len(window)
    total = reduce(lambda acc, x: acc + x, window, 0.0)
    mu = total / n
    var = reduce(lambda acc, x: acc + (x - mu) ** 2, window, 0.0) / n
    return mu, var


def sliding_windows(size: int, stream: Iterable[float]) -> Iterator[Tuple[float, ...]]:
    """Ventanas deslizantes inmutables (tuplas). Perezoso: funciona con generadores infinitos.
    El deque es estado local, no observable desde fuera (transparencia referencial)."""
    buf: deque = deque(maxlen=size)
    for x in stream:
        buf.append(x)
        if len(buf) == size:
            yield tuple(buf)


@curry
def moving_average(size: int, stream: Iterable[float]) -> Iterator[float]:
    return map(lambda w: mean_var(w)[0], sliding_windows(size, stream))


@curry
def detect_anomalies(threshold: float, size: int, readings: Iterable[Reading]) -> Iterator[Anomaly]:
    """Compara cada lectura con la ventana de las `size` lecturas PREVIAS (z-score).
    Uso currificado: detect_anomalies(3.0)(20)(stream)."""
    def pairs() -> Iterator[Tuple[Tuple[float, ...], Reading]]:
        buf: deque = deque(maxlen=size)
        for r in readings:
            if len(buf) == size:
                yield tuple(buf), r
            buf.append(r.value)

    def to_anomaly(pair: Tuple[Tuple[float, ...], Reading]) -> Anomaly:
        w, r = pair
        mu, var = mean_var(w)
        sd = var ** 0.5
        z = 0.0 if sd == 0 else (r.value - mu) / sd
        return Anomaly(r.t, r.value, mu, z)

    return filter(lambda a: abs(a.zscore) >= threshold, map(to_anomaly, pairs()))


# ---------- Cascarón imperativo (efectos secundarios aislados aquí) ----------
def source_stream(seed: int = 7, spike_every: int = 97) -> Iterator[Reading]:
    """Generador INFINITO de distancia frontal (m) con picos ocasionales (impuro: usa azar)."""
    rng = random.Random(seed)
    t = 0
    while True:
        v = 25.0 + rng.gauss(0, 0.3)
        if t > 0 and t % spike_every == 0:
            v -= 12.0
        yield Reading(t, v)
        t += 1


def main() -> None:
    anomalies = detect_anomalies(4.0)(30)(source_stream())
    for a in islice(anomalies, 5):  # solo se evalúa lo necesario (evaluación perezosa)
        print(f"t={a.t:5d}  valor={a.value:6.2f}  media={a.mean:6.2f}  z={a.zscore:6.2f}")
    values = (r.value for r in islice(source_stream(), 200))
    print("promedios móviles (primeros 3):", [round(x, 3) for x in islice(moving_average(10)(values), 3)])


if __name__ == "__main__":
    main()
