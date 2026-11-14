package br.com.advr;

import android.os.Bundle;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.util.concurrent.Callable;

public class TelaLoginActivity extends BaseActivity {

    private EditText campoEmail;
    private EditText campoSenha;
    private Button botaoAlternarSenha;
    private Button botaoEntrar;
    private Button botaoCadastrar;
    private Button botaoGoogle;
    private Button botaoFacebook;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tela_login);

        inicializarComponentes();
        configurarEventos();
    }

    private void inicializarComponentes() {
        campoEmail = findViewById(R.id.campo_email);
        campoSenha = findViewById(R.id.campo_senha);
        botaoAlternarSenha = findViewById(R.id.botao_alternar_senha);
        botaoEntrar = findViewById(R.id.botao_entrar);
        botaoCadastrar = findViewById(R.id.botao_cadastrar);
        botaoGoogle = findViewById(R.id.botao_google);
        botaoFacebook = findViewById(R.id.botao_facebook);
    }

    private void configurarEventos() {
        botaoEntrar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                realizarLogin();
            }
        });

        botaoAlternarSenha.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alternarVisibilidadeSenha((TextView) v);
            }
        });

        botaoCadastrar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                abrirTela(CadastroUsuarioActivity.class);
            }
        });

        botaoGoogle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exibirAvisoLoginSocial("Google");
            }
        });

        botaoFacebook.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exibirAvisoLoginSocial("Facebook");
            }
        });
    }

    private void realizarLogin() {
        final String emailDigitado = obterTextoCampo(R.id.campo_email);
        final String senhaDigitada = obterTextoCampo(R.id.campo_senha);

        if (exibirErroNoCampo(R.id.campo_email, Validadores.validarEmail(emailDigitado))) {
            return;
        }
        if (senhaDigitada.isEmpty()) {
            exibirErroNoCampo(R.id.campo_senha, "Informe a senha.");
            return;
        } else {
            exibirErroNoCampo(R.id.campo_senha, null);
        }

        executarEmSegundoPlano(new Callable<Long>() {
            @Override
            public Long call() {
                return obterRepositorio().autenticarUsuario(emailDigitado, senhaDigitada);
            }
        }, new CallbackResultado<Long>() {
            @Override
            public void aoConcluir(Long usuarioId) {
                obterAplicacao().usuarioLogadoId = usuarioId;
                campoSenha.setText("");
                abrirTela(PainelPrincipalActivity.class);
                finish();
            }
        });
    }

    private void alternarVisibilidadeSenha(TextView botaoTexto) {
        boolean senhaOculta = campoSenha.getTransformationMethod() instanceof PasswordTransformationMethod;
        if (senhaOculta) {
            campoSenha.setTransformationMethod(null);
            botaoTexto.setText("Ocultar senha");
        } else {
            campoSenha.setTransformationMethod(PasswordTransformationMethod.getInstance());
            botaoTexto.setText("Mostrar senha");
        }
        campoSenha.setSelection(campoSenha.length());
    }

    @Override
    protected void onResume() {
        super.onResume();
        executarEmSegundoPlano(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return obterRepositorio().precisaConfiguracaoInicial();
            }
        }, new CallbackResultado<Boolean>() {
            @Override
            public void aoConcluir(Boolean precisaConfigurar) {
                if (precisaConfigurar) {
                    abrirTela(ConfiguracaoInicialActivity.class);
                    finish();
                }
            }
        });
    }

    private void exibirAvisoLoginSocial(String provedor) {
        exibirDialogoTexto(
                "Login com " + provedor,
                "Esta versão utiliza login local com e-mail e senha. A autenticação real com "
                        + provedor + " ainda não está implementada. Consulte o guia de pendências da EPIC 1."
        );
    }
}
