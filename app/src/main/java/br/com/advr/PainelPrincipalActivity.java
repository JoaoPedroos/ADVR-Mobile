package br.com.advr;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import java.util.concurrent.Callable;

public class PainelPrincipalActivity extends TelaProtegidaActivity {

    private String abaAtual = "inicio";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_painel_principal);

        if (savedInstanceState != null) {
            abaAtual = savedInstanceState.getString("aba", "inicio");
        } else {
            abaAtual = getIntent().getStringExtra("aba");
        }
        if (abaAtual == null) {
            abaAtual = "inicio";
        }

        configurarBotoes();
        selecionarAba(abaAtual);
    }

    private void configurarBotoes() {
        findViewById(R.id.botao_abrir_perfil).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                abrirTela(PerfilUsuarioActivity.class);
            }
        });

        findViewById(R.id.botao_abrir_gestao).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                abrirTela(GestaoAdministrativaActivity.class);
            }
        });

        findViewById(R.id.botao_sair_conta).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                voltarParaLogin();
            }
        });

        findViewById(R.id.botao_atualizar_processos).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                carregarDadosPainel();
            }
        });

        findViewById(R.id.botao_abrir_processos).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selecionarAba("processos");
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("aba", abaAtual);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        selecionarAba(intent.getStringExtra("aba"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (obterUsuarioLogadoId() > 0) {
            carregarDadosPainel();
        }
    }

    public void selecionarAba(String proximaAba) {
        if ("processos".equals(proximaAba)) {
            abaAtual = "processos";
        } else {
            abaAtual = "inicio";
        }

        View painelInicio = findViewById(R.id.painel_inicio);
        View painelProcessos = findViewById(R.id.painel_processos);

        if ("inicio".equals(abaAtual)) {
            painelInicio.setVisibility(View.VISIBLE);
            painelProcessos.setVisibility(View.GONE);
        } else {
            painelInicio.setVisibility(View.GONE);
            painelProcessos.setVisibility(View.VISIBLE);
        }

        marcarAbaAtiva(abaAtual);
    }

    private void carregarDadosPainel() {
        final long usuarioId = obterUsuarioLogadoId();

        executarEmSegundoPlano(new Callable<Object[]>() {
            @Override
            public Object[] call() {
                Usuario usuario = obterRepositorio().buscarUsuarioPorId(usuarioId);
                List<Processo> listaProcessos = obterRepositorio().listarProcessos(usuarioId);
                int[] estatisticas = usuario.ehAdministrador()
                        ? obterRepositorio().obterEstatisticasPainel(usuarioId)
                        : new int[]{0, 0};
                return new Object[]{usuario, listaProcessos, estatisticas};
            }
        }, new CallbackResultado<Object[]>() {
            @Override
            public void aoConcluir(Object[] dados) {
                Usuario usuario = (Usuario) dados[0];
                @SuppressWarnings("unchecked")
                List<Processo> listaProcessos = (List<Processo>) dados[1];
                int[] estatisticas = (int[]) dados[2];

                atualizarCabecalhoPainel(usuario);
                atualizarIndicadores(usuario, listaProcessos, estatisticas);
                preencherListaProcessos(usuario, listaProcessos);
            }
        });
    }

    private void atualizarCabecalhoPainel(Usuario usuario) {
        atualizarBarraNavegacao(usuario, abaAtual);

        TextView textoBoasVindas = findViewById(R.id.texto_boas_vindas);
        TextView textoPerfil = findViewById(R.id.texto_perfil_painel);
        TextView textoSubtitulo = findViewById(R.id.texto_subtitulo_painel);
        View cartaoBoasVindas = findViewById(R.id.cartao_boas_vindas);

        textoBoasVindas.setText("Olá, " + usuario.nome + "!");
        textoPerfil.setText(formatarNomePerfil(usuario.perfil));

        if (usuario.ehAdministrador()) {
            textoSubtitulo.setText("Visão geral e gestão dos dados do escritório.");
            cartaoBoasVindas.setBackgroundResource(R.drawable.bg_hero_admin);
            findViewById(R.id.botao_abrir_gestao).setVisibility(View.VISIBLE);
            findViewById(R.id.bloco_metricas_admin).setVisibility(View.VISIBLE);
        } else if ("ADVOGADO".equals(usuario.perfil)) {
            textoSubtitulo.setText("Acompanhe seus processos em um só lugar.");
            cartaoBoasVindas.setBackgroundResource(R.drawable.bg_hero_lawyer);
            findViewById(R.id.botao_abrir_gestao).setVisibility(View.GONE);
            findViewById(R.id.bloco_metricas_admin).setVisibility(View.GONE);
        } else {
            textoSubtitulo.setText("Acompanhe seus processos em um só lugar.");
            cartaoBoasVindas.setBackgroundResource(R.drawable.bg_hero_client);
            findViewById(R.id.botao_abrir_gestao).setVisibility(View.GONE);
            findViewById(R.id.bloco_metricas_admin).setVisibility(View.GONE);
        }
    }

    private void atualizarIndicadores(Usuario usuario, List<Processo> listaProcessos, int[] estatisticas) {
        int totalComAdvogado = 0;
        for (Processo processo : listaProcessos) {
            if (processo.advogadoId > 0) {
                totalComAdvogado++;
            }
        }

        TextView indicadorTotal = findViewById(R.id.indicador_total_processos);
        TextView indicadorAtribuidos = findViewById(R.id.indicador_processos_atribuidos);
        TextView indicadorUsuarios = findViewById(R.id.indicador_total_usuarios);
        TextView indicadorAdvogados = findViewById(R.id.indicador_total_advogados);
        TextView tituloLista = findViewById(R.id.titulo_lista_processos);

        indicadorTotal.setText(String.valueOf(listaProcessos.size()));
        indicadorAtribuidos.setText(String.valueOf(totalComAdvogado));
        indicadorUsuarios.setText(String.valueOf(estatisticas[0]));
        indicadorAdvogados.setText(String.valueOf(estatisticas[1]));

        String prefixoTitulo = usuario.ehAdministrador() ? "Processos cadastrados" : "Meus processos";
        tituloLista.setText(prefixoTitulo + " (" + listaProcessos.size() + ")");
    }

    private void preencherListaProcessos(Usuario usuario, List<Processo> listaProcessos) {
        LinearLayout containerLista = findViewById(R.id.container_lista_processos);
        containerLista.removeAllViews();

        if (listaProcessos.isEmpty()) {
            TextView textoVazio = new TextView(this);
            textoVazio.setText("Nenhum processo vinculado. Entre em contato com o escritório.");
            textoVazio.setTextSize(14);
            textoVazio.setTextColor(getColor(R.color.muted));
            textoVazio.setBackgroundResource(R.drawable.bg_card);
            int espacamento = converterDpParaPx(22);
            textoVazio.setPadding(espacamento, espacamento, espacamento, espacamento);
            containerLista.addView(textoVazio);
            return;
        }

        for (int grupo = 0; grupo < 2; grupo++) {
            boolean semResponsavel = (grupo == 1);
            boolean grupoPossuiItens = false;

            for (Processo p : listaProcessos) {
                if ((p.advogadoId < 1) == semResponsavel) {
                    grupoPossuiItens = true;
                    break;
                }
            }

            if (!grupoPossuiItens) {
                continue;
            }

            TextView tituloGrupo = new TextView(this);
            tituloGrupo.setText(semResponsavel ? "Sem responsável" : "Com advogado responsável");
            tituloGrupo.setTextSize(14);
            tituloGrupo.setTextColor(getColor(R.color.ink));
            tituloGrupo.setTypeface(null, Typeface.BOLD);
            tituloGrupo.setPadding(0, converterDpParaPx(8), 0, converterDpParaPx(12));
            containerLista.addView(tituloGrupo);

            for (final Processo processo : listaProcessos) {
                if ((processo.advogadoId < 1) != semResponsavel) {
                    continue;
                }

                View cartaoProcesso = getLayoutInflater().inflate(R.layout.item_lista_processo, containerLista, false);
                TextView txtNumero = cartaoProcesso.findViewById(R.id.item_processo_numero);
                TextView txtTitulo = cartaoProcesso.findViewById(R.id.item_processo_titulo);
                TextView txtStatus = cartaoProcesso.findViewById(R.id.item_processo_status);
                TextView txtAdvogado = cartaoProcesso.findViewById(R.id.item_processo_advogado);

                txtNumero.setText(processo.numero);
                txtTitulo.setText(processo.titulo);
                txtStatus.setText(processo.status);
                txtAdvogado.setText("Responsável: " + processo.advogado);

                cartaoProcesso.setContentDescription(processo.numero + ". " + processo.titulo + ". " + processo.status + ". Responsável: " + processo.advogado + ". Ver detalhes.");
                cartaoProcesso.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(PainelPrincipalActivity.this, DetalhesProcessoActivity.class);
                        intent.putExtra("processo_id", processo.id);
                        startActivity(intent);
                    }
                });

                containerLista.addView(cartaoProcesso);
            }
        }
    }

    private int converterDpParaPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
