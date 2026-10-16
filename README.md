# Recordatorios Citas Service

![CI](https://github.com/XaviVillamizar/sanchezvillamizar-post1-u9/actions/workflows/ci.yml/badge.svg)

## Descripción
Repositorio del post-contenido de la Unidad 9 de Patrones de Diseño de Software.
Un único proyecto Spring Boot que envía recordatorios de citas médicas por el canal
preferido del paciente (correo o SMS). Se construyó desde el inicio con **Strategy**
(`ReminderChannel`) y **Factory Method** (`ReminderChannelFactory`), ambos inyectados
por constructor, y el foco del trabajo es **probar ese diseño**: pruebas unitarias con
JUnit 5 y Mockito, un refactor de Inversión de Dependencias (DIP), pruebas de
integración con H2 y un pipeline de CI en GitHub Actions.

## Estructura del proyecto

```
src/main/java/com/clinica/recordatorios/
├── RecordatoriosCitasServiceApplication.java
├── domain/       Cita, CanalNotificacion
├── repository/   CitaRepository
├── channel/      ReminderChannel (Strategy), EmailReminderChannel, SmsReminderChannel,
│                 ReminderChannelFactory, ReminderChannelFactoryImpl
│   └── gateway/  NotificadorSmsGateway, TwilioNotificadorSmsGateway
├── service/      CitaReminderService, CitaReminderServiceImpl, CitaNoEncontradaException
└── config/       ClockConfig, CanalUrgenteConfig (composition root)

src/test/java/com/clinica/recordatorios/
├── service/      CitaReminderServiceImplTest (unitarias, Mockito)
├── repository/   CitaRepositoryTest (@DataJpaTest)
└── CitaReminderServiceIntegrationTest (@SpringBootTest)
```

## Ejecutar las pruebas

```bash
mvn test      # Pruebas unitarias
mvn verify    # Unitarias + integración + reporte de cobertura (JaCoCo)
```

El reporte de cobertura queda en `target/site/jacoco/index.html`.

## Evidencia

| Prueba | Captura |
|---|---|
| Parte 1: 3 pruebas unitarias en verde | ![Parte 1]<img width="1920" height="1032" alt="image" src="https://github.com/user-attachments/assets/b127810d-9557-4739-9b84-06b58c3ee0e4" /> |
| Parte 2: 6 pruebas tras el refactor DIP | ![Refactor]<img width="1920" height="1032" alt="image" src="https://github.com/user-attachments/assets/943764f6-3455-4c8b-81c8-c3af025a8db3" /> |
| `mvn verify` con integración | ![Integración]<img width="1920" height="1032" alt="image" src="https://github.com/user-attachments/assets/8fe291bc-e1da-4f38-bd8c-53b0e33ac56a" /> |
| Cobertura JaCoCo | ![Cobertura]<img width="1920" height="1032" alt="image" src="https://github.com/user-attachments/assets/6dd25a52-5cc3-4695-8fb4-cf03df577e56" /> |
| Pipeline de GitHub Actions | ![Actions]<img width="1920" height="1032" alt="image" src="https://github.com/user-attachments/assets/fd58bf5f-6429-43ba-9a8e-79d57d7050ad" /> |

Cobertura de instrucciones del proyecto: **81%**. El número total de pruebas es **9**.

## Decisiones de diseño

### Mock, stub o fake para cada colaborador (Parte 1)

| Colaborador | Doble elegido | Justificación |
|---|---|---|
| `CitaRepository` | **Mock** | No basta con stubbear `findByFechaHoraCitaBetween...` para que devuelva una lista: también hay que verificar que `save()` se llama exactamente una vez y con la cita marcada como enviada. Cuando una prueba necesita controlar la respuesta y verificar la interacción, es un Mock y no un simple Stub. |
| `ReminderChannelFactory` y `ReminderChannel` | **Mock** | El comportamiento bajo prueba es la selección de estrategia: que el servicio pida el canal `EMAIL` cuando la cita lo prefiere y que ese canal reciba el mensaje. Eso solo se comprueba verificando la interacción, no con un valor de retorno. |
| `Clock` | **Fake** (`Clock.fixed`) | `Clock.fixed(...)` es una implementación real y completa de `java.time.Clock`, provista por el JDK: `LocalDateTime.now(clock)` funciona de verdad, solo con el instante congelado. Mockear `Clock` exigiría stubbear `instant()` y `getZone()` por separado y arriesgaría que no se comporte como un reloj real. |

### Qué patrón facilitó qué prueba

| Patrón | Prueba que facilitó | Por qué sin el patrón sería más difícil |
|---|---|---|
| **Strategy** (`ReminderChannel`) | `enviarRecordatoriosPendientes_citaEnVentana_usaElCanalPreferido` | Sin la interfaz, la lógica de envío (SMTP o SMS) estaría escrita en línea en el servicio, y probar la selección de canal exigiría mockear librerías de bajo nivel (cliente SMTP, cliente HTTP). Con Strategy, un solo `@Mock ReminderChannel` verifica cualquier canal. |
| **Factory Method** (`ReminderChannelFactory`) | La misma prueba, y `..._sinCitasPendientes_noConsultaLaFactory` | Sin la fábrica, el servicio tendría un `if/else` o `switch` embebido, y esa decisión solo sería observable de forma indirecta. Con la fábrica inyectada, la selección es una interacción explícita: `verify(reminderChannelFactory).obtenerCanal(EMAIL)`. |
| **DIP / inyección por constructor** | Las tres pruebas de `enviarRecordatorioUrgente`, en especial `..._gatewayFalla_noMarcaComoEnviado` | Sin inyección no existe ningún punto donde sustituir el canal por un mock que simule el fallo del gateway. |

### Diagnóstico y refactor DIP (Parte 2)

**Antes.** La primera versión de `enviarRecordatorioUrgente` construía su colaborador dentro del método:

```java
ReminderChannel canalUrgente = new SmsReminderChannel(new TwilioNotificadorSmsGateway());
canalUrgente.enviar(cita, "URGENTE: su cita es en menos de 2 horas y no ha sido confirmada");
```

La línea responsable es la construcción con `new` dentro del cuerpo del método. Mockito solo sustituye objetos que le llegan a la clase por constructor o por campo; aquí el canal nace dentro del método, así que no hay dónde insertar un test double y es imposible simular que el gateway de SMS falle. Se podría interceptar el `new` con `Mockito.mockConstruction()`, pero eso es una señal de que el problema es el diseño, no la herramienta.

Contraste con `enviarRecordatoriosPendientes()`: ese método sí era testeable desde la Parte 1 porque la fábrica y el reloj llegan por constructor.

**Después.** El `new` no se elimina, se mueve al único lugar donde es correcto: el composition root.

```java
@Configuration
public class CanalUrgenteConfig {
    @Bean
    public ReminderChannel canalUrgente(NotificadorSmsGateway gateway) {
        return new SmsReminderChannel(gateway);
    }
}
```

El servicio recibe `@Qualifier("canalUrgente") ReminderChannel` por constructor. El comportamiento externo no cambió, y la prueba `enviarRecordatorioUrgente_gatewayFalla_noMarcaComoEnviado` pasó de imposible a trivial. Esa prueba es la evidencia de que la testabilidad es consecuencia directa de aplicar DIP.

**Ajuste necesario.** `canalUrgente` es una instancia de `SmsReminderChannel`, que también es un `@Component`. Para evitar `NoUniqueBeanDefinitionException`, `ReminderChannelFactoryImpl` pide el bean por nombre con `@Qualifier("smsReminderChannel")`.

### Por qué H2 real pero gateway mockeado
En la prueba de integración conviven una base de datos real (H2 en memoria) y un `@MockitoBean` sobre `NotificadorSmsGateway`. No es inconsistente: H2 es un Fake aceptable porque su contrato SQL/JPA es equivalente al de la base real, sin infraestructura adicional. Un proveedor de SMS de terceros no tiene un equivalente gratuito y determinista, y reemplazarlo con un Mock evita depender de credenciales, red y disponibilidad del proveedor.

### Sin contenedor de base de datos en CI
El workflow no incluye un bloque `services:`. Es posible porque las pruebas de integración usan H2 en memoria. Probar contra PostgreSQL con Testcontainers exigiría un runner con Docker y tiempos de arranque mayores.

### Detalles técnicos relevantes
- **Spring Boot 4.** El proyecto usa Boot 4.1. `@MockBean` ya no existe, así que se usa `@MockitoBean`, y `@DataJpaTest` vive en `org.springframework.boot.data.jpa.test.autoconfigure`.
- **Bean del reloj de prueba.** El bean del `@TestConfiguration` se llama `clockFijo` y no `clock`, porque `ClockConfig` ya define `clock` y Spring Boot no permite sobrescribir beans por nombre.
- **Aislamiento de la integración.** La prueba limpia la tabla en `@BeforeEach` para no depender de datos de otras pruebas.

## Herramientas utilizadas
- Java 21, Spring Boot 4, Spring Data JPA, H2, Lombok
- JUnit 5, Mockito, JaCoCo
- Apache Maven, GitHub Actions, Git, GitHub, VS Code

## Conclusiones
Este laboratorio mostró que la testabilidad no depende de herramientas más potentes sino del diseño. Strategy y Factory Method reducen lo que hay que simular a una sola interfaz propia, y la inyección por constructor convierte cada colaborador en un punto de sustitución. El refactor DIP lo demostró de forma concreta: una prueba que era imposible de escribir pasó a ser trivial al mover un `new` al composition root, sin cambiar el comportamiento externo. Elegir entre mock, stub y fake obliga a preguntarse qué se quiere comprobar: una interacción, un valor, o una implementación real y determinista como `Clock.fixed`. Por último, usar H2 en las pruebas de integración simplificó también el pipeline de CI, porque no necesita contenedores.
