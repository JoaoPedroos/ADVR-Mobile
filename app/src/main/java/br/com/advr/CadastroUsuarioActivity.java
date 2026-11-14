package br.com.advr;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.util.concurrent.Callable;

public class CadastroUsuarioActivity extends BaseActivity {

    protected boolean ehPrimeiroAcesso() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_usuario);

        Button botaoVoltar = findViewById(R.id.botao_voltar);
        Button botaoSalvar = findViewById(R.id.botao_salvar_cadastro);

        if (ehPrimeiroAcesso()) {
            TextView titulo = findViewById(R.id.texto_titulo_cadastro);
            TextView descricao = findViewById(R.id.texto_descricao_cadastro);
            titulo.setText("Primeiro acesso");
            descricao.setText("Crie o administrador inicial deste banco local. Os próximos cadastros serão de clientes.");
            botaoSalvar.setText("Criar administrador");
            botaoVoltar.setVisibility(View.GONE);
        }

        botaoVoltar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        botaoSalvar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                salvarNovoUsuario();
            }
        });
    }

    private void salvarNovoUsuario() {
        final String nome = obterTextoCampo(R.id.campo_nome);
        final String email = obterTextoCampo(R.id.campo_email);
        final String cpf = obterTextoCampo(R.id.campo_cpf);
        final String senha = obterTextoCampo(R.id.campo_senha);
        final String confirmarSenha = obterTextoCampo(R.id.campo_confirmar_senha);

        boolean possuiErro = false;

        if (exibirErroNoCampo(R.id.campo_nome, Validadores.validarNome(nome))) {
            possuiErro = true;
        }
        if (exibirErroNoCampo(R.id.campo_email, Validadores.validarEmail(email))) {
            possuiErro = true;
        }
        String erroCpf = Validadores.cpfValido(cpf) ? null : "Informe um CPF válido.";
        if (exibirErroNoCampo(R.id.campo_cpf, erroCpf)) {
            possuiErro = true;
        }
        if (exibirErroNoCampo(R.id.campo_senha, Validadores.validarSenha(senha))) {
            possuiErro = true;
        }
        String erroConfirmacao = senha.equals(confirmarSenha) ? null : "As senhas não coincidem.";
        if (exibirErroNoCampo(R.id.campo_confirmar_senha, erroConfirmacao)) {
            possuiErro = true;
        }

        if (possuiErro) {
            return;
        }

        executarEmSegundoPlano(new Callable<Long>() {
            @Override
            public Long call() {
                return obterRepositorio().cadastrarUsuario(nome, email, cpf, senha, ehPrimeiroAcesso());
            }
        }, new CallbackResultado<Long>() {
            @Override
            public void aoConcluir(Long novoId) {
                new AlertDialog.Builder(CadastroUsuarioActivity.this)
                        .setTitle("Cadastro realizado")
                        .setMessage("Conta criada. Entre com seu e-mail e senha. Esta versão local não envia e-mail de confirmação.")
                        .setCancelable(false)
                        .setPositiveButton("Ir para login", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                voltarParaLogin();
                            }
                        })
                        .show();
            }
        });
    }
}
