package br.com.advr;

import android.app.Application;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdvrApp extends Application {
    public final ExecutorService executorTarefas = Executors.newSingleThreadExecutor();
    public volatile long usuarioLogadoId = -1;
    public Repositorio repositorio;

    @Override
    public void onCreate() {
        super.onCreate();
        repositorio = new Repositorio(this);
    }
}
