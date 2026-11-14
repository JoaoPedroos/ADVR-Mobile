package br.com.advr;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import java.util.concurrent.Callable;

public class DetalhesProcessoActivity extends TelaProtegidaActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detalhes_processo);

        findViewById(R.id.botao_voltar_processos).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                abrirPainelPrincipal("processos");
            }
        });

        findViewById(R.id.botao_simular_captura).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                simularNovaCaptura();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (obterUsuarioLogadoId() < 1) {
            return;
        }
        carregarDetalhesDoProcesso();
    }

    private void simularNovaCaptura() {
        final long processoId = getIntent().getLongExtra("processo_id", -1);
        final long usuarioId = obterUsuarioLogadoId();

        executarEmSegundoPlano(new Callable<Long>() {
            @Override
            public Long call() {
                return obterRepositorio().simularCapturaMovimentacao(usuarioId, processoId);
            }
        }, new CallbackResultado<Long>() {
            @Override
            public void aoConcluir(Long movimentacaoId) {
                exibirMensagem("Nova movimentação capturada (pendente de revisão).");
                carregarDetalhesDoProcesso();
            }
        });
    }

    private void carregarDetalhesDoProcesso() {
        final long processoId = getIntent().getLongExtra("processo_id", -1);
        final long usuarioId = obterUsuarioLogadoId();

        executarEmSegundoPlano(new Callable<Object[]>() {
            @Override
            public Object[] call() {
                Processo processo = obterRepositorio().buscarDetalhesProcesso(usuarioId, processoId);
                Usuario usuario = obterRepositorio().buscarUsuarioPorId(usuarioId);
                List<Movimentacao> movimentacoes = obterRepositorio().listarMovimentacoes(usuarioId, processoId);
                return new Object[]{processo, usuario, movimentacoes};
            }
        }, new CallbackResultado<Object[]>() {
            @Override
            public void aoConcluir(Object[] dados) {
                Processo processo = (Processo) dados[0];
                Usuario usuario = (Usuario) dados[1];
                @SuppressWarnings("unchecked")
                List<Movimentacao> listaMovimentacoes = (List<Movimentacao>) dados[2];

                exibirDadosProcesso(usuario, processo);
                exibirListaMovimentacoes(usuario, usuarioId, listaMovimentacoes);
            }
        }, new CallbackResultado<Exception>() {
            @Override
            public void aoConcluir(Exception erro) {
                new AlertDialog.Builder(DetalhesProcessoActivity.this)
                        .setMessage(erro.getMessage())
                        .setCancelable(false)
                        .setPositiveButton("Voltar ao painel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                        .show();
            }
        });
    }

    private void exibirDadosProcesso(Usuario usuario, Processo processo) {
        atualizarBarraNavegacao(usuario, "processos");

        TextView txtNumero = findViewById(R.id.detalhe_numero_processo);
        TextView txtTitulo = findViewById(R.id.detalhe_titulo_processo);
        TextView txtStatus = findViewById(R.id.detalhe_status_processo);
        TextView txtAdvogado = findViewById(R.id.detalhe_advogado_processo);
        TextView txtDescricao = findViewById(R.id.detalhe_descricao_processo);

        txtNumero.setText(processo.numero);
        txtTitulo.setText(processo.titulo);
        txtStatus.setText(processo.status);
        txtAdvogado.setText(processo.advogado);

        if (processo.descricao.isEmpty()) {
            txtDescricao.setText("Nenhuma descrição informada.");
        } else {
            txtDescricao.setText(processo.descricao);
        }
    }

    private void exibirListaMovimentacoes(Usuario usuario, final long usuarioId, List<Movimentacao> movimentacoes) {
        boolean podeModerar = !"CLIENTE".equals(usuario.perfil);

        Button botaoSimular = findViewById(R.id.botao_simular_captura);
        TextView textoInstrucao = findViewById(R.id.texto_instrucao_movimentacoes);

        if (podeModerar) {
            botaoSimular.setVisibility(View.VISIBLE);
            textoInstrucao.setText("Revise as publicações capturadas e escreva a explicação em linguagem simples para liberar ao cliente.");
        } else {
            botaoSimular.setVisibility(View.GONE);
            textoInstrucao.setText("Veja abaixo as atualizações traduzidas pelo seu advogado.");
        }

        LinearLayout containerLista = findViewById(R.id.container_lista_movimentacoes);
        containerLista.removeAllViews();

        if (movimentacoes.isEmpty()) {
            TextView textoVazio = new TextView(this);
            if (podeModerar) {
                textoVazio.setText("Nenhuma movimentação registrada ainda. Clique no botão acima para simular uma captura.");
            } else {
                textoVazio.setText("Nenhuma movimentação liberada pelo advogado até o momento.");
            }
            textoVazio.setTextSize(14);
            textoVazio.setTextColor(getColor(R.color.muted));
            textoVazio.setPadding(0, converterDpParaPx(6), 0, converterDpParaPx(6));
            containerLista.addView(textoVazio);
            return;
        }

        for (final Movimentacao mov : movimentacoes) {
            LinearLayout cartaoMov = new LinearLayout(this);
            cartaoMov.setOrientation(LinearLayout.VERTICAL);
            cartaoMov.setBackgroundResource(R.drawable.bg_card);
            int espacamento = converterDpParaPx(14);
            cartaoMov.setPadding(espacamento, espacamento, espacamento, espacamento);

            LinearLayout.LayoutParams paramsCartao = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            paramsCartao.topMargin = converterDpParaPx(10);
            cartaoMov.setLayoutParams(paramsCartao);

            TextView etiquetaStatus = new TextView(this);
            String textoStatus = mov.estaLiberada() ? "Liberada ao cliente" : "Pendente de revisão";
            etiquetaStatus.setText(mov.fonte + " • " + textoStatus);
            etiquetaStatus.setTextSize(12);
            etiquetaStatus.setTypeface(null, Typeface.BOLD);
            etiquetaStatus.setTextColor(getColor(mov.estaLiberada() ? R.color.success : R.color.primary_dark));
            etiquetaStatus.setBackgroundResource(mov.estaLiberada() ? R.drawable.bg_green : R.drawable.bg_badge);
            etiquetaStatus.setPadding(converterDpParaPx(10), converterDpParaPx(4), converterDpParaPx(10), converterDpParaPx(4));
            cartaoMov.addView(etiquetaStatus);

            TextView txtTituloMov = new TextView(this);
            txtTituloMov.setText(mov.titulo);
            txtTituloMov.setTextSize(15);
            txtTituloMov.setTypeface(null, Typeface.BOLD);
            txtTituloMov.setTextColor(getColor(R.color.ink));
            txtTituloMov.setPadding(0, converterDpParaPx(10), 0, converterDpParaPx(2));
            cartaoMov.addView(txtTituloMov);

            String dataFormatada = mov.dataMovimentacao;
            if (dataFormatada != null && dataFormatada.length() >= 10) {
                dataFormatada = dataFormatada.substring(0, 10);
            }
            TextView txtDataMov = new TextView(this);
            txtDataMov.setText("Data: " + dataFormatada);
            txtDataMov.setTextSize(12);
            txtDataMov.setTextColor(getColor(R.color.muted));
            txtDataMov.setPadding(0, 0, 0, converterDpParaPx(8));
            cartaoMov.addView(txtDataMov);

            if (podeModerar) {
                TextView rotuloOriginal = new TextView(this);
                rotuloOriginal.setText("Texto original (" + mov.fonte + "):");
                rotuloOriginal.setTextSize(12);
                rotuloOriginal.setTypeface(null, Typeface.BOLD);
                rotuloOriginal.setTextColor(getColor(R.color.muted));
                cartaoMov.addView(rotuloOriginal);

                TextView txtOriginal = new TextView(this);
                txtOriginal.setText(mov.textoOriginal);
                txtOriginal.setTextSize(13);
                txtOriginal.setTextColor(getColor(R.color.ink));
                txtOriginal.setPadding(0, converterDpParaPx(2), 0, converterDpParaPx(10));
                cartaoMov.addView(txtOriginal);

                TextView rotuloTraducao = new TextView(this);
                rotuloTraducao.setText("Explicação para o cliente:");
                rotuloTraducao.setTextSize(12);
                rotuloTraducao.setTypeface(null, Typeface.BOLD);
                rotuloTraducao.setTextColor(getColor(R.color.primary_dark));
                cartaoMov.addView(rotuloTraducao);

                final EditText campoTraducao = new EditText(this);
                campoTraducao.setText(mov.traducaoJuridica);
                campoTraducao.setHint("Digite a tradução em linguagem simples...");
                campoTraducao.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                campoTraducao.setMinLines(2);
                campoTraducao.setTextSize(14);
                campoTraducao.setTextColor(getColor(R.color.ink));
                cartaoMov.addView(campoTraducao);

                Button botaoLiberar = new Button(this);
                botaoLiberar.setText(mov.estaLiberada() ? "Atualizar tradução liberada" : "Liberar para o cliente");
                botaoLiberar.setAllCaps(false);
                botaoLiberar.setTextColor(getColor(R.color.surface));
                botaoLiberar.setBackgroundResource(R.drawable.bg_primary);
                botaoLiberar.setPadding(converterDpParaPx(12), converterDpParaPx(10), converterDpParaPx(12), converterDpParaPx(10));

                LinearLayout.LayoutParams paramsBotao = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                paramsBotao.topMargin = converterDpParaPx(8);
                botaoLiberar.setLayoutParams(paramsBotao);

                final long movimentacaoId = mov.id;
                botaoLiberar.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final String explicacaoDigitada = campoTraducao.getText().toString();
                        executarEmSegundoPlano(new Callable<Boolean>() {
                            @Override
                            public Boolean call() {
                                obterRepositorio().liberarMovimentacaoParaCliente(usuarioId, movimentacaoId, explicacaoDigitada);
                                return true;
                            }
                        }, new CallbackResultado<Boolean>() {
                            @Override
                            public void aoConcluir(Boolean sucesso) {
                                exibirMensagem("Movimentação liberada para o cliente.");
                                carregarDetalhesDoProcesso();
                            }
                        });
                    }
                });
                cartaoMov.addView(botaoLiberar);
            } else {
                TextView txtTraducaoCliente = new TextView(this);
                txtTraducaoCliente.setText(mov.traducaoJuridica);
                txtTraducaoCliente.setTextSize(14);
                txtTraducaoCliente.setTextColor(getColor(R.color.ink));
                txtTraducaoCliente.setPadding(0, converterDpParaPx(2), 0, converterDpParaPx(8));
                cartaoMov.addView(txtTraducaoCliente);

                TextView txtPublicacaoOficial = new TextView(this);
                txtPublicacaoOficial.setText("Publicação oficial: " + mov.textoOriginal);
                txtPublicacaoOficial.setTextSize(12);
                txtPublicacaoOficial.setTextColor(getColor(R.color.muted));
                cartaoMov.addView(txtPublicacaoOficial);
            }

            containerLista.addView(cartaoMov);
        }
    }

    private int converterDpParaPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
