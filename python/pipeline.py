from functools import reduce

def procesar_telemetria_sensor(lecturas):
    """
    Pipeline funcional puro utilizando map, filter y reduce
    para procesar flujos de telemetría y detectar anomalías.
    """
    # 1. Filtrar valores con ruido o anomalías críticas
    lecturas_validas = filter(lambda x: x['intensidad'] > 0.2, lecturas)
    
    # 2. Mapear aplicando una transformación de calibración de distancia
    lecturas_calibradas = map(lambda x: {**x, 'distancia': x['distancia'] * 1.05}, lecturas_validas)
    
    # 3. Reducir para retornar la lista procesada de forma pura
    return list(lecturas_calibradas)
