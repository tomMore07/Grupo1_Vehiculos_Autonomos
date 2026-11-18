"""Módulo 3 (Funcional): pipeline puro para telemetría de sensores.

Núcleo funcional (puro, sin I/O ni estructuras mutables): moving_average, detect_anomalies, curry.
Cascarón imperativo (efectos): source_stream y main() al final.
"""
from __future__ import annotations

import argparse
import json
import random
from dataclasses import asdict, dataclass
from functools import reduce, wraps
from itertools import accumulate, count, islice
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
    """Ventanas deslizantes como tuplas inmutables, sin estructuras mutables.

    Se define como un *scan* (accumulate): cada ventana nueva se construye a partir de la anterior
    con una función pura (ventana + (x,))[-size:], que devuelve una tupla nueva y no modifica nada.
    Es perezoso: funciona con generadores infinitos.
    """
    nueva_ventana = lambda w, x: (w + (x,))[-size:]
    return filter(lambda w: len(w) == size, accumulate(stream, nueva_ventana, initial=()))


@curry
def moving_average(size: int, stream: Iterable[float]) -> Iterator[float]:
    return map(lambda w: mean_var(w)[0], sliding_windows(size, stream))


@curry
def detect_anomalies(threshold: float, size: int, readings: Iterable[Reading]) -> Iterator[Anomaly]:
    """Compara cada lectura con la ventana de las `size` lecturas PREVIAS (z-score).
    Uso currificado: detect_anomalies(3.0)(20)(stream). Sin estructuras mutables: es un scan puro."""
    def paso(estado, r):                       # estado = (ventana_previa_actualizada, (ventana_previa, lectura))
        ventana, _ = estado
        return ((ventana + (r.value,))[-size:], (ventana, r))

    def to_anomaly(par: Tuple[Tuple[float, ...], Reading]) -> Anomaly:
        w, r = par
        mu, var = mean_var(w)
        sd = var ** 0.5
        z = 0.0 if sd == 0 else (r.value - mu) / sd
        return Anomaly(r.t, r.value, mu, z)

    estados = islice(accumulate(readings, paso, initial=((), ())), 1, None)   # se omite el estado inicial
    pares = filter(lambda p: len(p[0]) == size, map(lambda e: e[1], estados))
    return filter(lambda a: abs(a.zscore) >= threshold, map(to_anomaly, pares))


# ---------- Cascarón imperativo (efectos secundarios aislados aquí) ----------
def source_stream(seed: int = 7, spike_every: int = 97) -> Iterator[Reading]:
    """Generador INFINITO de distancia frontal (m) con picos ocasionales (impuro: usa azar)."""
    rng = random.Random(seed)
    for t in count():
        v = 25.0 + rng.gauss(0, 0.3)
        if t > 0 and t % spike_every == 0:
            v -= 12.0
        yield Reading(t, v)


def anomaly_to_json(a: Anomaly) -> str:
    """Pura: serializa una anomalía al contrato JSON consumido por Java."""
    return json.dumps({k: round(v, 4) if isinstance(v, float) else v for k, v in asdict(a).items()})


def main() -> None:
    ap = argparse.ArgumentParser(description="Pipeline funcional de telemetría")
    ap.add_argument("--json", action="store_true", help="una anomalía por línea en JSON (contrato con Java)")
    ap.add_argument("--limit", type=int, default=5, help="número máximo de anomalías a emitir")
    args = ap.parse_args()

    anomalies = detect_anomalies(4.0)(30)(source_stream())
    taken = islice(anomalies, args.limit)  # evaluación perezosa: solo se calcula lo necesario
    if args.json:
        for a in taken:
            print(anomaly_to_json(a))
        return
    for a in taken:
        print(f"t={a.t:5d}  valor={a.value:6.2f}  media={a.mean:6.2f}  z={a.zscore:6.2f}")
    values = (r.value for r in islice(source_stream(), 200))
    print("promedios móviles (primeros 3):", [round(x, 3) for x in islice(moving_average(10)(values), 3)])


if __name__ == "__main__":
    main()
