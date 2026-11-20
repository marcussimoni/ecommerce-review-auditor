package br.com.ecommercereviewauditor.config;

import org.h2.tools.Server;

import java.sql.SQLException;

public class H2Console {

    private static Server webServer = null;
    public static void startServer() {
        // Inicia o console web na porta 8082
        try {
            webServer = Server.createWebServer("-web", "-webAllowOthers", "-webPort", "8082").start();
            System.out.println("H2 Console rodando em: " + webServer.getURL());
        } catch (SQLException e) {
            System.out.println("Falha ao iniciar o H2 Console");
        }

    }

    public static void stopServer() {
        try {
            System.out.println("Encerrando o H2 Console");
            webServer.stop();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Falha ao encerrar o H2 Console");
        }
    }

}
