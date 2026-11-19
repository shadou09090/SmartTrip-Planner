package com.project.back_end_proyecto.entity;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.dialect.OracleDialect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class OracleJpaMappingTest {
    @Test
    void allFourteenDocumentedTablesBuildOracleHibernateMetadataWithoutDatabaseAccess() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", OracleDialect.class.getName())
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .applySetting("hibernate.connection.provider_class",
                        "org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl")
                .build();
        try {
            MetadataSources sources = new MetadataSources(registry)
                    .addAnnotatedClass(User.class)
                    .addAnnotatedClass(Viaje.class)
                    .addAnnotatedClass(Interes.class)
                    .addAnnotatedClass(ViajeInteres.class)
                    .addAnnotatedClass(Actividad.class)
                    .addAnnotatedClass(ActividadInteres.class)
                    .addAnnotatedClass(Itinerario.class)
                    .addAnnotatedClass(DiaItinerario.class)
                    .addAnnotatedClass(ItinerarioActividad.class)
                    .addAnnotatedClass(Alojamiento.class)
                    .addAnnotatedClass(Vuelo.class)
                    .addAnnotatedClass(HistorialPrecioVuelo.class)
                    .addAnnotatedClass(ColaboradorViaje.class)
                    .addAnnotatedClass(Invitacion.class);
            Metadata metadata = sources.buildMetadata();

            assertNotNull(metadata.getEntityBinding(User.class.getName()));
            assertNotNull(metadata.getEntityBinding(Viaje.class.getName()));
            assertNotNull(metadata.getEntityBinding(Interes.class.getName()));
            assertNotNull(metadata.getEntityBinding(ViajeInteres.class.getName()));
            assertNotNull(metadata.getEntityBinding(Actividad.class.getName()));
            assertNotNull(metadata.getEntityBinding(ActividadInteres.class.getName()));
            assertNotNull(metadata.getEntityBinding(Itinerario.class.getName()));
            assertNotNull(metadata.getEntityBinding(DiaItinerario.class.getName()));
            assertNotNull(metadata.getEntityBinding(ItinerarioActividad.class.getName()));
            assertNotNull(metadata.getEntityBinding(Alojamiento.class.getName()));
            assertNotNull(metadata.getEntityBinding(Vuelo.class.getName()));
            assertNotNull(metadata.getEntityBinding(HistorialPrecioVuelo.class.getName()));
            assertNotNull(metadata.getEntityBinding(ColaboradorViaje.class.getName()));
            assertNotNull(metadata.getEntityBinding(Invitacion.class.getName()));
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
