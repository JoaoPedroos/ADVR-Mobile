package br.com.advr;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import java.util.Locale;
import java.util.concurrent.Callable;

public class PerfilUsuarioActivity extends TelaProtegidaActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil_usuario);

        findViewById(R.id.campo_email).setEnabled(false);
        findViewById(R.id.campo_cpf).setEnabled(false);

        findViewById(R.id.botao_voltar).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        findViewById(R.id.botao_salvar_perfil).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                salvarAlteracoesPerfil();
            }
        });

        findViewById(R.id.botao_alterar_senha).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                salvarNovaSenha();
            }
        });

        if (obterUsuarioLogadoId() > 0) {
            carregarDadosUsuario();
        }
    }

    private void carregarDadosUsuario() {
        final long usuarioId = obterUsuarioLogadoId();

        executarEmSegundoPlano(new Callable<Usuario>() {
            @Override
            public Usuario call() {
                return obterRepositorio().buscarUsuarioPorId(usuarioId);
            }
        }, new CallbackResultado<Usuario>() {
            @Override
            public void aoConcluir(Usuario usuario) {
                atualizarBarraNavegacao(usuario, "perfil");

                TextView txtAvatar = findViewById(R.id.texto_avatar_perfil);
                TextView txtNome = findViewById(R.id.texto_nome_perfil);
                TextView txtEmail = findViewById(R.id.texto_email_perfil);
                TextView txtTipoPerfil = findViewById(R.id.texto_tipo_perfil);

                String inicial = usuario.nome.substring(0, 1).toUpperCase(Locale.ROOT);
                txtAvatar.setText(inicial);
                txtNome.setText(usuario.nome);
                txtEmail.setText(usuario.email);
                txtTipoPerfil.setText(formatarNomePerfil(usuario.perfil));

                ((EditText) findViewById(R.id.campo_nome)).setText(usuario.nome);
                ((EditText) findViewById(R.id.campo_telefone)).setText(usuario.telefone);
                ((EditText) findViewById(R.id.campo_email)).setText(usuario.email);
                ((EditText) findViewById(R.id.campo_cpf)).setText(usuario.cpf);
            }
        });
    }

    private void salvarAlteracoesPerfil() {
        final String nome = obterTextoCampo(R.id.campo_nome);
        final String telefone = obterTextoCampo(R.id.campo_telefone);

        if (exibirErroNoCampo(R.id.campo_nome, Validadores.validarNome(nome))) {
            return;
        }

        final long usuarioId = obterUsuarioLogadoId();
        executarEmSegundoPlano(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                obterRepositorio().atualizarPerfilUsuario(usuarioId, nome, telefone);
                return true;
            }
        }, new CallbackResultado<Boolean>() {
            @Override
            public void aoConcluir(Boolean sucesso) {
                String nomeLimpo = nome.trim();
                TextView txtNome = findViewById(R.id.texto_nome_perfil);
                TextView txtAvatar = findViewById(R.id.texto_avatar_perfil);

                txtNome.setText(nomeLimpo);
                txtAvatar.setText(nomeLimpo.substring(0, 1).toUpperCase(Locale.ROOT));
                exibirMensagem("Dados atualizados com sucesso.");
            }
        });
    }

    private void salvarNovaSenha() {
        final String senhaAtual = obterTextoCampo(R.id.campo_senha_atual);
        final String novaSenha = obterTextoCampo(R.id.campo_nova_senha);
        final String confirmarSenha = obterTextoCampo(R.id.campo_confirmar_senha);

        if (senhaAtual.isEmpty()) {
            exibirErroNoCampo(R.id.campo_senha_atual, "Informe a senha atual.");
            return;
        } else {
            exibirErroNoCampo(R.id.campo_senha_atual, null);
        }

        if (exibirErroNoCampo(R.id.campo_nova_senha, Validadores.validarSenha(novaSenha))) {
            return;
        }

        String erroConfirmacao = novaSenha.equals(confirmarSenha) ? null : "As senhas não coincidem.";
        if (exibirErroNoCampo(R.id.campo_confirmar_senha, erroConfirmacao)) {
            return;
        }

        final long usuarioId = obterUsuarioLogadoId();
        executarEmSegundoPlano(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                obterRepositorio().alterarSenhaUsuario(usuarioId, senhaAtual, novaSenha);
                return true;
            }
        }, new CallbackResultado<Boolean>() {
            @Override
            public void aoConcluir(Boolean sucesso) {
                ((EditText) findViewById(R.id.campo_senha_atual)).setText("");
                ((EditText) findViewById(R.id.campo_nova_senha)).setText("");
                ((EditText) findViewById(R.id.campo_confirmar_senha)).setText("");
                exibirMensagem("Senha atualizada com sucesso.");
            }
        });
    }
}
