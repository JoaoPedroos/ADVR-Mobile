package br.com.advr;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.concurrent.Callable;

public class BaseActivity extends Activity {

    protected boolean tarefaEmExecucao;

    protected AdvrApp obterAplicacao() {
        return (AdvrApp) getApplication();
    }

    protected Repositorio obterRepositorio() {
        return obterAplicacao().repositorio;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        ajustarBarrasSistema();
    }

    private void ajustarBarrasSistema() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            ViewGroup conteudo = findViewById(android.R.id.content);
            View raiz = conteudo.getChildAt(0);
            if (raiz != null) {
                raiz.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                    @Override
                    public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                        Insets barras = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                        view.setPadding(barras.left, barras.top, barras.right, barras.bottom);
                        return insets;
                    }
                });
                raiz.requestApplyInsets();
            }
        }
    }

    protected String obterTextoCampo(int campoId) {
        EditText campo = findViewById(campoId);
        return campo.getText().toString();
    }

    protected boolean exibirErroNoCampo(int campoId, String mensagemErro) {
        EditText campo = findViewById(campoId);
        campo.setError(mensagemErro);
        if (mensagemErro != null) {
            campo.requestFocus();
            return true;
        }
        return false;
    }

    protected void exibirMensagem(String texto) {
        TextView caixaMensagem = findViewById(R.id.texto_mensagem);
        if (caixaMensagem == null) {
            return;
        }
        caixaMensagem.setText(texto);
        if (texto == null || texto.isEmpty()) {
            caixaMensagem.setVisibility(View.GONE);
        } else {
            caixaMensagem.setVisibility(View.VISIBLE);
        }
    }

    protected void abrirTela(Class<?> telaDestino) {
        Intent intent = new Intent(this, telaDestino);
        startActivity(intent);
    }

    protected void voltarParaLogin() {
        obterAplicacao().usuarioLogadoId = -1;
        Intent intent = new Intent(this, TelaLoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    protected void exibirDialogoTexto(String titulo, String conteudo) {
        TextView textoCorpo = new TextView(this);
        textoCorpo.setText(conteudo);
        textoCorpo.setTextSize(16);
        textoCorpo.setTextIsSelectable(true);
        textoCorpo.setPadding(24, 16, 24, 16);

        ScrollView rolagem = new ScrollView(this);
        rolagem.addView(textoCorpo);

        new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setView(rolagem)
                .setPositiveButton("Fechar", null)
                .show();
    }

    protected interface CallbackResultado<T> {
        void aoConcluir(T resultado);
    }

    protected <T> void executarEmSegundoPlano(Callable<T> tarefa, CallbackResultado<T> aoSucesso) {
        executarEmSegundoPlano(tarefa, aoSucesso, new CallbackResultado<Exception>() {
            @Override
            public void aoConcluir(Exception erro) {
                String msg = erro.getMessage();
                if (msg == null || msg.isEmpty()) {
                    msg = "Não foi possível concluir a operação.";
                }
                exibirMensagem(msg);
            }
        });
    }

    protected <T> void executarEmSegundoPlano(final Callable<T> tarefa,
                                              final CallbackResultado<T> aoSucesso,
                                              final CallbackResultado<Exception> aoFalhar) {
        if (tarefaEmExecucao) {
            return;
        }
        tarefaEmExecucao = true;
        alterarEstadoCarregamento(true);
        exibirMensagem("");

        obterAplicacao().executorTarefas.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final T resultado = tarefa.call();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }
                            tarefaEmExecucao = false;
                            alterarEstadoCarregamento(false);
                            aoSucesso.aoConcluir(resultado);
                        }
                    });
                } catch (final Exception erro) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }
                            tarefaEmExecucao = false;
                            alterarEstadoCarregamento(false);
                            aoFalhar.aoConcluir(erro);
                        }
                    });
                }
            }
        });
    }

    private void alterarEstadoCarregamento(boolean carregando) {
        View barraProgresso = findViewById(R.id.barra_progresso);
        if (barraProgresso != null) {
            barraProgresso.setVisibility(carregando ? View.VISIBLE : View.GONE);
        }
        if (carregando) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        }
    }

    @Override
    public void onBackPressed() {
        if (!tarefaEmExecucao) {
            super.onBackPressed();
        }
    }
}
