# Arquitectura y especificación UML

El módulo Java tiene **13 clases** (se eliminaron 4 que no eran esenciales: `EstadoVehiculo`, `ResultadoFrenado`, `JsonPlano` y `PercepcionException`).
Los diagramas están en Mermaid (se renderizan en GitHub/GitLab/VS Code). La versión en PDF, con las mismas figuras dibujadas con Graphviz, es `01_arquitectura_uml.pdf`.

## 1. Diagrama de clases (UML)
Visibilidades: `+` público, `-` privado, `#` protegido · `*` método abstracto · `$` estático.

```mermaid
classDiagram
direction TB

class NivelRiesgo {
  <<enumeration>>
  BAJO
  MEDIO
  ALTO
  CRITICO
}

class Obstaculo {
  <<record>>
  +double distanciaM
  +double velocidadRelativaMs
}

class Anomalia {
  <<record>>
  +long t
  +double valor
  +double zscore
}

class EventoTelemetria {
  <<record>>
  +NivelRiesgo nivel
  +String descripcion
}

class EstrategiaEvaluacionRiesgo {
  <<interface>>
  +evaluar(Obstaculo obs) NivelRiesgo
}

class RiesgoPorDistancia {
  -double umbralCriticoM
  -double umbralAltoM
  +evaluar(Obstaculo obs) NivelRiesgo
}

class RiesgoPorTiempoColision {
  -double ttcCriticoS
  -double ttcAltoS
  +evaluar(Obstaculo obs) NivelRiesgo
}

class ActuadorFrenado {
  <<abstract>>
  -List~String~ bitacora
  +ejecutarSecuenciaFrenado(double presion) boolean
  #verificarSistema()* boolean
  #aplicarPresion(double presion)* void
  #registrarEvento(String msg) void
  +getBitacora() List~String~
}

class FrenoHidraulico {
  -double nivelLiquido
  -double presionLineaBar
  #verificarSistema() boolean
  #aplicarPresion(double presion) void
}

class FrenoRegenerativo {
  -double cargaBateria
  -double torqueNm
  #verificarSistema() boolean
  #aplicarPresion(double presion) void
}

class ProveedorPercepcion {
  <<interface>>
  +obstaculoMasCercano() Optional~Obstaculo~
  +anomaliasRecientes() List~Anomalia~
}

class AdaptadorKernelCpp {
  -Path raiz
  -int nPuntos
  -double velocidadVehiculoMs
  -int limiteAnomalias
  +obstaculoMasCercano() Optional~Obstaculo~
  +anomaliasRecientes() List~Anomalia~
  +leerCampo(String json, String clave)$ double
  -ejecutar(List~String~ cmd, Path dir) List~String~
}

class MotorDecision {
  -EstrategiaEvaluacionRiesgo estrategia
  -ActuadorFrenado actuador
  -List~EventoTelemetria~ historial
  +MotorDecision(EstrategiaEvaluacionRiesgo e, ActuadorFrenado a)
  +evaluarYActuar(Obstaculo obs) NivelRiesgo
  +procesarPercepcion(ProveedorPercepcion p) Optional~NivelRiesgo~
  +setEstrategia(EstrategiaEvaluacionRiesgo e) void
  +getHistorial() List~EventoTelemetria~
  -frenar(double presion, String tipo) String
}

EstrategiaEvaluacionRiesgo <|.. RiesgoPorDistancia
EstrategiaEvaluacionRiesgo <|.. RiesgoPorTiempoColision
ActuadorFrenado <|-- FrenoHidraulico
ActuadorFrenado <|-- FrenoRegenerativo
ProveedorPercepcion <|.. AdaptadorKernelCpp

MotorDecision o-- EstrategiaEvaluacionRiesgo : estrategia
MotorDecision o-- ActuadorFrenado : actuador
MotorDecision *-- "0..*" EventoTelemetria : historial
MotorDecision ..> ProveedorPercepcion : usa
EstrategiaEvaluacionRiesgo ..> Obstaculo
ProveedorPercepcion ..> Obstaculo
ProveedorPercepcion ..> Anomalia
EventoTelemetria --> NivelRiesgo

note for ActuadorFrenado "Template Method: ejecutarSecuenciaFrenado() es final y fija el orden verificar → aplicar → registrar"
note for EstrategiaEvaluacionRiesgo "Strategy: política de riesgo intercambiable (OCP)"
note for AdaptadorKernelCpp "Adapter: traduce JSON de C++ y Python a objetos del dominio"
```


Relaciones: `<|--` generalización · `<|..` realización · `o--` agregación · `*--` composición · `-->` asociación · `..>` dependencia.

## 2. Patrones GoF
| Patrón | Dónde | Justificación |
|---|---|---|
| **Template Method** | `ActuadorFrenado.ejecutarSecuenciaFrenado()` (`final`) | El orden verificar → aplicar → registrar es una regla de seguridad que ninguna subclase puede alterar. `FrenoHidraulico` y `FrenoRegenerativo` solo implementan los pasos variables; `registrarEvento` es un *hook*. |
| **Strategy** | `EstrategiaEvaluacionRiesgo` + 2 implementaciones | La política de riesgo cambia en ejecución (`setEstrategia`) sin modificar `MotorDecision`. |
| **Adapter** | `AdaptadorKernelCpp` implementa `ProveedorPercepcion` | Adapta procesos externos (C++, Python) y JSON a la interfaz que espera el dominio. Si se migra a JNI solo cambia esta clase. |

## 3. Principios SOLID
| Principio | Evidencia |
|---|---|
| **S** (responsabilidad única) | `MotorDecision` solo orquesta; las estrategias solo evalúan; el adaptador solo traduce. |
| **O** (abierto/cerrado) | Un freno o una política nuevos son clases nuevas; no se modifica código existente. |
| **L** (sustitución de Liskov) | Cualquier `ActuadorFrenado` o estrategia se puede usar donde se espera el tipo base (pruebas "LSP"). |
| **I** (segregación de interfaces) | Interfaces mínimas: `EstrategiaEvaluacionRiesgo` (1 método) y `ProveedorPercepcion` (2). |
| **D** (inversión de dependencias) | `MotorDecision` depende de abstracciones (`EstrategiaEvaluacionRiesgo`, `ActuadorFrenado`, `ProveedorPercepcion`), no de C++ ni de Python. |

## 4. Reglas de encapsulamiento
| Regla | Cómo se cumple |
|---|---|
| Campos privados | Todo el estado es `private`; los `record` tienen campos `final`. |
| Invariantes validados al construir | Constructores compactos de `record` y constructores de frenos/estrategias lanzan `IllegalArgumentException`. |
| **Sin aliasing de colecciones** | `getHistorial()` y `getBitacora()` devuelven `List.copyOf(...)`: una **instantánea inmutable**, no una vista de la lista interna. Los eventos nuevos no aparecen en ella y recorrerla no lanza `ConcurrentModificationException`. |
| Dependencias no nulas | `Objects.requireNonNull` en constructores y `setEstrategia`. |
| Seguridad entre hilos | Métodos públicos de `MotorDecision` y `ActuadorFrenado` son `synchronized`. |

## 5. Diagrama de flujo de datos (DFD, notación Yourdon–DeMarco)

**Notación:** rectángulo = entidad externa (E) · círculo = proceso numerado (1.0, 2.0…) · dos líneas paralelas = almacén de datos (D) · flecha rotulada = flujo de datos. Las entidades externas no se comunican directamente con los almacenes, y todo flujo entra o sale de un proceso.

### 5.1 Nivel 0 (diagrama de contexto)
```mermaid
flowchart LR
    E1["E1 · Fuente LiDAR simulada"]
    E2["E2 · Fuente de telemetría simulada"]
    E3["E3 · Operador"]
    P0(("0<br/>Procesar percepción<br/>y simular frenado"))

    E1 -->|"Nube de puntos LiDAR"| P0
    E2 -->|"Lecturas de telemetría"| P0
    P0 -->|"Nivel de riesgo e historial de eventos"| E3
```

### 5.2 Nivel 1 (descomposición del proceso 0)
```mermaid
flowchart LR
    E1["E1 · Fuente LiDAR simulada"]
    E2["E2 · Fuente de telemetría simulada"]
    E3["E3 · Operador"]

    P1(("1.0<br/>Filtrar nube y localizar<br/>obstáculo · C++17/OpenMP"))
    P2(("2.0<br/>Detectar anomalías<br/>Python"))
    P3(("3.0<br/>Adaptar datos de<br/>percepción · Java"))
    P4(("4.0<br/>Evaluar riesgo y<br/>decidir frenado · Java"))
    P5(("5.0<br/>Simular secuencia<br/>de frenado · Java"))
    P6(("6.0<br/>Presentar resultado<br/>de la ejecución · Java"))

    D1@{ shape: datastore, label: "D1 · Historial de eventos" }
    D2@{ shape: datastore, label: "D2 · Bitácora del actuador" }

    E1 -->|"Nube de puntos LiDAR"| P1
    E2 -->|"Lecturas de telemetría"| P2

    P1 -->|"Distancia al obstáculo más cercano (JSON)"| P3
    P2 -->|"Anomalías detectadas (JSON por línea)"| P3
    P3 -->|"Obstáculo opcional y lista de anomalías"| P4

    P4 -->|"Presión normalizada de frenado, si aplica"| P5
    P5 -->|"Resultado de la secuencia de frenado"| P4
    P5 -->|"Registros de la secuencia"| D2

    P4 -->|"Eventos de anomalía y de evaluación de riesgo"| D1
    P4 -->|"Nivel de riesgo o ausencia de obstáculo"| P6
    D1 -->|"Instantánea inmutable del historial"| P6
    P6 -->|"Nivel de riesgo e historial de eventos"| E3
```

### 5.3 Diccionario de datos
| Flujo | Contenido | Implementación |
|---|---|---|
| nube de puntos | arreglos `x, y, z, intensidad` (float) | `lidar::PointCloud` (C++, SoA) |
| obstáculo más cercano | distancia (m) o −1 si no hay | JSON de `lidar_kernel --json` → `Obstaculo` |
| lecturas de telemetría | `(t, valor)` | generador infinito `source_stream` → `Reading` |
| anomalías | `(t, valor, media, zscore)` | JSON de `pipeline.py --json` → `Anomalia` |
| nivel de riesgo | `BAJO`/`MEDIO`/`ALTO`/`CRITICO` | enum `NivelRiesgo` |
| orden de frenado | presión en [0, 1] | parámetro de `ejecutarSecuenciaFrenado` |
| historial / bitácora | lista de eventos | `List.copyOf(...)` (instantánea inmutable) |

## 6. Diagrama de secuencia (ciclo de decisión)
## 6. Diagrama de secuencia (ciclo de decisión)

```mermaid
sequenceDiagram
    actor O as Operador
    participant J as Main (Java)
    participant M as MotorDecision
    participant A as AdaptadorKernelCpp
    participant P as Pipeline Python
    participant C as Kernel LiDAR C++17
    participant R as EstrategiaEvaluacionRiesgo
    participant F as ActuadorFrenado

    O->>J: Ejecutar demostración
    J->>M: procesarPercepcion(percepcion)

    Note over M,A: Adapter · el motor usa la interfaz ProveedorPercepcion
    M->>A: anomaliasRecientes()
    A->>P: Ejecutar pipeline con límite de anomalías
    P-->>A: Una anomalía por línea en JSON
    A-->>M: Lista de anomalías

    loop Por cada anomalía recibida
        M->>M: Registrar evento de telemetría con nivel MEDIO
    end

    M->>A: obstaculoMasCercano()
    A->>C: Ejecutar kernel LiDAR con salida JSON
    C-->>A: JSON con min_obstacle_dist
    A-->>M: Optional de Obstaculo

    alt Hay un obstáculo
        M->>M: evaluarYActuar(obstaculo)
        Note over M,R: Strategy · la política de evaluación es intercambiable
        M->>R: evaluar(obstaculo)
        R-->>M: NivelRiesgo

        alt Riesgo CRÍTICO o ALTO
            M->>M: Seleccionar presión 1.0 para CRÍTICO o 0.5 para ALTO
            M->>F: ejecutarSecuenciaFrenado(presion)
            Note over F: Template Method · el método final fija la secuencia
            F->>F: Registrar inicio en la bitácora
            F->>F: verificarSistema()
            alt Verificación exitosa
                F->>F: aplicarPresion(presion)
                F->>F: Registrar frenado aplicado
                F-->>M: true
            else Verificación fallida
                F->>F: Registrar frenado abortado
                F-->>M: false
            end
        else Riesgo MEDIO o BAJO
            Note over M: Continúa con el registro del evento de riesgo
        end

        M->>M: Registrar evento de riesgo y resultado del frenado, si aplica
        M-->>J: Optional con el nivel de riesgo
    else No hay obstáculo
        M-->>J: Optional.empty()
    end

    J->>M: getHistorial()
    M-->>J: Instantánea inmutable del historial
    J-->>O: Mostrar nivel de riesgo o SIN OBSTÁCULO y el historial
```

Las anomalías se registran como eventos de telemetría con nivel `MEDIO`. La estrategia evalúa el riesgo de frenado cuando la percepción devuelve un obstáculo.
