from pipeline import procesar_telemetria_sensor

def test_pipeline_funcional():
    datos_prueba = [
        {'id': 1, 'intensidad': 0.1, 'distancia': 10.0},
        {'id': 2, 'intensidad': 0.8, 'distancia': 15.0},
        {'id': 3, 'intensidad': 0.5, 'distancia': 20.0}
    ]
    
    resultado = procesar_telemetria_sensor(datos_prueba)
    
    # Verificación de inmutabilidad y filtrado correcto
    assert len(resultado) == 2
    assert resultado[0]['id'] == 2
    print("¡Pruebas del Pipeline Funcional pasadas con éxito!")

if __name__ == "__main__":
    test_pipeline_funcional()
