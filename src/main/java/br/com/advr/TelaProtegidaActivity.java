package br.com.advr;

import android.content.Intent;
import android.view.View;
import android.widget.TextView;

public class TelaProtegidaActivity extends BaseActivity {

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        configurarCabecalhoENavegacao();
    }

    private void configurarCabecalhoENavegacao() {
        findViewById(R.id.cabecalho_sair).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                voltarParaLogin();
            }
        });

        findViewById(R.id.menu_inicio).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                abrirPainelPrincipal("inicio");
            }
        });

        findViewById(R.id.menu_processos).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                abrirPainelPrincipal("processos");
            }
        });

        findViewById(R.id.menu_perfil).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!(TelaProtegidaActivity.this instanceof PerfilUsuarioActivity)) {
                    abrirTela(PerfilUsuarioActivity.class);
                    if (!(TelaProtegidaActivity.this instanceof PainelPrincipalActivity)) {
                        finish();
                    }
                }
            }
        });

        findViewById(R.id.menu_gestao).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!(TelaProtegidaActivity.this instanceof GestaoAdministrativaActivity)) {
                    abrirTela(GestaoAdministrativaActivity.class);
                    if (!(TelaProtegidaActivity.this instanceof PainelPrincipalActivity)) {
                        finish();
                    }
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (obterAplicacao().usuarioLogadoId < 1) {
            voltarParaLogin();
        }
    }

    protected long obterUsuarioLogadoId() {
        return obterAplicacao().usuarioLogadoId;
    }

    protected void abrirPainelPrincipal(String abaDestino) {
        if (this instanceof PainelPrincipalActivity) {
            ((PainelPrincipalActivity) this).selecionarAba(abaDestino);
            return;
        }
        Intent intent = new Intent(this, PainelPrincipalActivity.class);
        intent.putExtra("aba", abaDestino);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    protected void atualizarBarraNavegacao(Usuario usuario, String abaSelecionada) {
        TextView textoPerfilCabecalho = findViewById(R.id.cabecalho_perfil);
        textoPerfilCabecalho.setText("Perfil: " + formatarNomePerfil(usuario.perfil));

        View botaoGestao = findViewById(R.id.menu_gestao);
        if (usuario.ehAdministrador()) {
            botaoGestao.setVisibility(View.VISIBLE);
        } else {
            botaoGestao.setVisibility(View.GONE);
        }

        marcarAbaAtiva(abaSelecionada);
    }

    protected void marcarAbaAtiva(String abaSelecionada) {
        findViewById(R.id.menu_inicio).setSelected("inicio".equals(abaSelecionada));
        findViewById(R.id.menu_processos).setSelected("processos".equals(abaSelecionada));
        findViewById(R.id.menu_perfil).setSelected("perfil".equals(abaSelecionada));
        findViewById(R.id.menu_gestao).setSelected("gestao".equals(abaSelecionada));
    }

    protected String formatarNomePerfil(String perfil) {
        if ("ADMINISTRADOR".equals(perfil)) {
            return "Administrador";
        }
        if ("ADVOGADO".equals(perfil)) {
            return "Advogado";
        }
        return "Cliente";
    }
}
