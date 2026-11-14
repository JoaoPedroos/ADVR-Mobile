package br.com.advr;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

public class GestaoAdministrativaActivity extends TelaProtegidaActivity {

    private static final int CODIGO_SELECIONAR_JSON = 41;
    private static final String[] LISTA_PERFIS = {"CLIENTE", "ADVOGADO", "ADMINISTRADOR"};

    private long processoEmEdicaoId = -1;
    private final List<Usuario> listaUsuarios = new ArrayList<>();
    private final List<Usuario> listaAdvogados = new ArrayList<>();
    private final List<Processo> listaProcessos = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gestao_administrativa);

        preencherSpinner(R.id.seletor_perfis, Arrays.asList(LISTA_PERFIS));
        configurarBotoes();
        limparFormularioProcesso();

        if (obterUsuarioLogadoId() > 0) {
            atualizarDadosTela(null);
        }
    }

    private void configurarBotoes() {
        findViewById(R.id.botao_voltar).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        findViewById(R.id.botao_buscar_usuarios).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                atualizarDadosTela(null);
            }
        });

        findViewById(R.id.botao_salvar_perfil).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmarAlteracaoPerfil();
            }
        });

        findViewById(R.id.botao_novo_processo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                limparFormularioProcesso();
            }
        });

        findViewById(R.id.botao_carregar_processo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                carregarProcessoSelecionado();
            }
        });

        findViewById(R.id.botao_salvar_processo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                salvarDadosProcesso();
            }
        });

        findViewById(R.id.botao_remover_cliente).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                abrirDialogoRemoverCliente();
            }
        });

        findViewById(R.id.botao_historico_auditoria).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                consultarHistoricoAuditoria();
            }
        });

        findViewById(R.id.botao_importar_json).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                abrirSeletorArquivoJson();
            }
        });

        Spinner seletorUsuarios = findViewById(R.id.seletor_usuarios);
        seletorUsuarios.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= listaUsuarios.size()) {
                    return;
                }
                String perfilAtual = listaUsuarios.get(position).perfil;
                selecionarPerfilNoSpinner(perfilAtual);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void selecionarPerfilNoSpinner(String perfil) {
        Spinner seletorPerfis = findViewById(R.id.seletor_perfis);
        for (int i = 0; i < LISTA_PERFIS.length; i++) {
            if (LISTA_PERFIS[i].equals(perfil)) {
                seletorPerfis.setSelection(i);
                break;
            }
        }
    }

    private <T> void preencherSpinner(int spinnerId, List<T> itens) {
        Spinner spinner = findViewById(spinnerId);
        ArrayAdapter<T> adaptador = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, itens);
        adaptador.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adaptador);
    }

    private void atualizarDadosTela(final String avisoPosAtualizacao) {
        final long adminId = obterUsuarioLogadoId();
        final String textoBusca = obterTextoCampo(R.id.campo_busca_usuario);

        executarEmSegundoPlano(new Callable<Object[]>() {
            @Override
            public Object[] call() {
                List<Usuario> usuarios = obterRepositorio().listarUsuarios(adminId, textoBusca, "");
                List<Usuario> advogados = obterRepositorio().listarUsuarios(adminId, "", "ADVOGADO");
                List<Processo> processos = obterRepositorio().listarProcessos(adminId);
                Usuario adminAtual = obterRepositorio().buscarUsuarioPorId(adminId);
                return new Object[]{usuarios, advogados, processos, adminAtual};
            }
        }, new CallbackResultado<Object[]>() {
            @Override
            public void aoConcluir(Object[] resultado) {
                listaUsuarios.clear();
                listaAdvogados.clear();
                listaProcessos.clear();

                @SuppressWarnings("unchecked")
                List<Usuario> usuariosEncontrados = (List<Usuario>) resultado[0];
                @SuppressWarnings("unchecked")
                List<Usuario> advogadosEncontrados = (List<Usuario>) resultado[1];
                @SuppressWarnings("unchecked")
                List<Processo> processosEncontrados = (List<Processo>) resultado[2];
                Usuario adminAtual = (Usuario) resultado[3];

                listaUsuarios.addAll(usuariosEncontrados);
                listaAdvogados.addAll(advogadosEncontrados);
                listaProcessos.addAll(processosEncontrados);

                atualizarBarraNavegacao(adminAtual, "gestao");
                montarCartoesUsuarios();

                preencherSpinner(R.id.seletor_usuarios, listaUsuarios);

                List<String> opcoesAdvogados = new ArrayList<>();
                opcoesAdvogados.add("Sem responsável");
                for (Usuario adv : listaAdvogados) {
                    opcoesAdvogados.add(adv.nome + " • " + adv.cpf);
                }
                preencherSpinner(R.id.seletor_advogados, opcoesAdvogados);
                preencherSpinner(R.id.seletor_processos, listaProcessos);

                limparFormularioProcesso();
                if (avisoPosAtualizacao != null) {
                    exibirMensagem(avisoPosAtualizacao);
                }
            }
        }, new CallbackResultado<Exception>() {
            @Override
            public void aoConcluir(Exception erro) {
                new AlertDialog.Builder(GestaoAdministrativaActivity.this)
                        .setMessage(erro.getMessage())
                        .setCancelable(false)
                        .setPositiveButton("Voltar", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                        .show();
            }
        });
    }

    private void montarCartoesUsuarios() {
        LinearLayout containerCards = findViewById(R.id.container_cards_usuarios);
        containerCards.removeAllViews();

        for (int i = 0; i < listaUsuarios.size(); i++) {
            final Usuario usuario = listaUsuarios.get(i);
            final int posicao = i;

            View cartao = getLayoutInflater().inflate(R.layout.item_lista_usuario, containerCards, false);
            TextView txtNome = cartao.findViewById(R.id.item_usuario_nome);
            TextView txtEmail = cartao.findViewById(R.id.item_usuario_email);
            TextView txtPerfil = cartao.findViewById(R.id.item_usuario_perfil);

            txtNome.setText(usuario.nome);
            txtEmail.setText(usuario.email);
            txtPerfil.setText(formatarNomePerfil(usuario.perfil));

            cartao.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Spinner seletorUsuarios = findViewById(R.id.seletor_usuarios);
                    seletorUsuarios.setSelection(posicao);
                    selecionarPerfilNoSpinner(usuario.perfil);
                    findViewById(R.id.seletor_perfis).requestFocus();
                    exibirMensagem("Usuário selecionado: " + usuario.nome);
                }
            });

            containerCards.addView(cartao);
        }
    }

    private void confirmarAlteracaoPerfil() {
        Spinner seletorUsuarios = findViewById(R.id.seletor_usuarios);
        Spinner seletorPerfis = findViewById(R.id.seletor_perfis);

        int indiceUsuario = seletorUsuarios.getSelectedItemPosition();
        if (indiceUsuario < 0 || indiceUsuario >= listaUsuarios.size()) {
            exibirMensagem("Busque e selecione um usuário.");
            return;
        }

        final Usuario usuarioAlvo = listaUsuarios.get(indiceUsuario);
        final String novoPerfil = LISTA_PERFIS[seletorPerfis.getSelectedItemPosition()];

        new AlertDialog.Builder(this)
                .setTitle("Confirmar perfil")
                .setMessage("Alterar " + usuarioAlvo.nome + " para " + novoPerfil + "?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Alterar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final long adminId = obterUsuarioLogadoId();
                        executarEmSegundoPlano(new Callable<Boolean>() {
                            @Override
                            public Boolean call() {
                                obterRepositorio().alterarPerfilAcesso(adminId, usuarioAlvo.id, novoPerfil);
                                return true;
                            }
                        }, new CallbackResultado<Boolean>() {
                            @Override
                            public void aoConcluir(Boolean sucesso) {
                                atualizarDadosTela("Perfil atualizado. Alteração registrada no histórico.");
                            }
                        });
                    }
                })
                .show();
    }

    private void limparFormularioProcesso() {
        processoEmEdicaoId = -1;

        ((EditText) findViewById(R.id.campo_numero_processo)).setText("");
        ((EditText) findViewById(R.id.campo_titulo_processo)).setText("");
        ((EditText) findViewById(R.id.campo_descricao_processo)).setText("");
        ((EditText) findViewById(R.id.campo_cpf_cliente)).setText("");
        ((EditText) findViewById(R.id.campo_situacao_processo)).setText("Em andamento");

        Spinner seletorAdvogados = findViewById(R.id.seletor_advogados);
        seletorAdvogados.setSelection(0);

        TextView textoStatus = findViewById(R.id.texto_status_edicao);
        textoStatus.setText("Novo processo");
    }

    private void carregarProcessoSelecionado() {
        Spinner seletorProcessos = findViewById(R.id.seletor_processos);
        int indice = seletorProcessos.getSelectedItemPosition();
        if (indice < 0 || indice >= listaProcessos.size()) {
            exibirMensagem("Nenhum processo disponível.");
            return;
        }

        final long adminId = obterUsuarioLogadoId();
        final long processoId = listaProcessos.get(indice).id;

        executarEmSegundoPlano(new Callable<Object[]>() {
            @Override
            public Object[] call() {
                Processo processo = obterRepositorio().buscarDetalhesProcesso(adminId, processoId);
                List<Usuario> clientes = obterRepositorio().listarClientesVinculados(adminId, processoId);
                return new Object[]{processo, clientes};
            }
        }, new CallbackResultado<Object[]>() {
            @Override
            public void aoConcluir(Object[] dados) {
                Processo processo = (Processo) dados[0];
                @SuppressWarnings("unchecked")
                List<Usuario> clientesVinculados = (List<Usuario>) dados[1];

                processoEmEdicaoId = processo.id;

                ((EditText) findViewById(R.id.campo_numero_processo)).setText(processo.numero);
                ((EditText) findViewById(R.id.campo_titulo_processo)).setText(processo.titulo);
                ((EditText) findViewById(R.id.campo_descricao_processo)).setText(processo.descricao);
                ((EditText) findViewById(R.id.campo_situacao_processo)).setText(processo.status);
                ((EditText) findViewById(R.id.campo_cpf_cliente)).setText("");

                int posicaoAdvogado = 0;
                for (int i = 0; i < listaAdvogados.size(); i++) {
                    if (listaAdvogados.get(i).id == processo.advogadoId) {
                        posicaoAdvogado = i + 1;
                        break;
                    }
                }
                ((Spinner) findViewById(R.id.seletor_advogados)).setSelection(posicaoAdvogado);

                StringBuilder nomesClientes = new StringBuilder();
                for (Usuario cliente : clientesVinculados) {
                    nomesClientes.append("\n").append(cliente.nome).append(" • ").append(cliente.cpf);
                }

                String textoClientes = nomesClientes.length() == 0 ? " nenhum" : nomesClientes.toString();
                TextView textoStatus = findViewById(R.id.texto_status_edicao);
                textoStatus.setText("Editando #" + processo.id + "\nClientes vinculados:" + textoClientes);
            }
        });
    }

    private void salvarDadosProcesso() {
        final String numero = obterTextoCampo(R.id.campo_numero_processo);
        final String titulo = obterTextoCampo(R.id.campo_titulo_processo);
        final String descricao = obterTextoCampo(R.id.campo_descricao_processo);
        final String situacao = obterTextoCampo(R.id.campo_situacao_processo);
        final String cpfCliente = obterTextoCampo(R.id.campo_cpf_cliente);

        if (exibirErroNoCampo(R.id.campo_numero_processo, numero.trim().isEmpty() ? "Informe o número." : null)) {
            return;
        }
        if (exibirErroNoCampo(R.id.campo_titulo_processo, titulo.trim().isEmpty() ? "Informe o título." : null)) {
            return;
        }

        boolean exigeCpfValido = (processoEmEdicaoId < 0 || !cpfCliente.isEmpty());
        if (exigeCpfValido && !Validadores.cpfValido(cpfCliente)) {
            exibirErroNoCampo(R.id.campo_cpf_cliente, "Informe um CPF válido de cliente cadastrado.");
            return;
        } else {
            exibirErroNoCampo(R.id.campo_cpf_cliente, null);
        }

        Spinner seletorAdvogados = findViewById(R.id.seletor_advogados);
        int indiceAdvogado = seletorAdvogados.getSelectedItemPosition();
        final long advogadoId = (indiceAdvogado > 0 && indiceAdvogado <= listaAdvogados.size())
                ? listaAdvogados.get(indiceAdvogado - 1).id
                : -1;

        final long adminId = obterUsuarioLogadoId();
        final long idParaSalvar = processoEmEdicaoId;

        executarEmSegundoPlano(new Callable<Long>() {
            @Override
            public Long call() {
                return obterRepositorio().salvarProcesso(adminId, idParaSalvar, numero, titulo, descricao, situacao, cpfCliente, advogadoId);
            }
        }, new CallbackResultado<Long>() {
            @Override
            public void aoConcluir(Long processoSalvoId) {
                atualizarDadosTela("Processo salvo. Vínculos e alterações registrados.");
            }
        });
    }

    private void abrirDialogoRemoverCliente() {
        if (processoEmEdicaoId < 1) {
            exibirMensagem("Carregue primeiro o processo que deseja alterar.");
            return;
        }

        final long adminId = obterUsuarioLogadoId();
        final long processoId = processoEmEdicaoId;

        executarEmSegundoPlano(new Callable<List<Usuario>>() {
            @Override
            public List<Usuario> call() {
                return obterRepositorio().listarClientesVinculados(adminId, processoId);
            }
        }, new CallbackResultado<List<Usuario>>() {
            @Override
            public void aoConcluir(final List<Usuario> clientesVinculados) {
                if (clientesVinculados.isEmpty()) {
                    exibirMensagem("Este processo não tem cliente vinculado.");
                    return;
                }

                String[] nomes = new String[clientesVinculados.size()];
                for (int i = 0; i < clientesVinculados.size(); i++) {
                    nomes[i] = clientesVinculados.get(i).toString();
                }

                new AlertDialog.Builder(GestaoAdministrativaActivity.this)
                        .setTitle("Selecione o vínculo para remover")
                        .setItems(nomes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                final Usuario clienteEscolhido = clientesVinculados.get(which);
                                confirmarRemocaoCliente(adminId, processoId, clienteEscolhido);
                            }
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
            }
        });
    }

    private void confirmarRemocaoCliente(final long adminId, final long processoId, final Usuario cliente) {
        new AlertDialog.Builder(this)
                .setMessage("Remover o acesso de " + cliente.nome + " a este processo?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Remover", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        executarEmSegundoPlano(new Callable<Boolean>() {
                            @Override
                            public Boolean call() {
                                obterRepositorio().removerVinculoCliente(adminId, processoId, cliente.id);
                                return true;
                            }
                        }, new CallbackResultado<Boolean>() {
                            @Override
                            public void aoConcluir(Boolean sucesso) {
                                atualizarDadosTela("Vínculo removido e registrado no histórico.");
                            }
                        });
                    }
                })
                .show();
    }

    private void consultarHistoricoAuditoria() {
        final long adminId = obterUsuarioLogadoId();
        executarEmSegundoPlano(new Callable<String>() {
            @Override
            public String call() {
                return obterRepositorio().gerarRelatorioAuditoria(adminId);
            }
        }, new CallbackResultado<String>() {
            @Override
            public void aoConcluir(String textoRelatorio) {
                exibirDialogoTexto("Histórico de alterações", textoRelatorio);
            }
        });
    }

    private void abrirSeletorArquivoJson() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(intent, CODIGO_SELECIONAR_JSON);
        } catch (ActivityNotFoundException e) {
            exibirMensagem("Nenhum seletor de arquivos disponível neste dispositivo.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != CODIGO_SELECIONAR_JSON || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        final Uri arquivoUri = data.getData();
        final long adminId = obterUsuarioLogadoId();

        executarEmSegundoPlano(new Callable<String>() {
            @Override
            public String call() throws Exception {
                String conteudoJson;
                try (InputStream entrada = getContentResolver().openInputStream(arquivoUri);
                     ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
                    if (entrada == null) {
                        throw new IllegalArgumentException("Não foi possível ler o arquivo.");
                    }
                    byte[] buffer = new byte[4096];
                    int lidos;
                    int totalBytes = 0;
                    while ((lidos = entrada.read(buffer)) != -1) {
                        totalBytes += lidos;
                        if (totalBytes > 1048576) {
                            throw new IllegalArgumentException("O JSON deve ter até 1 MB.");
                        }
                        saida.write(buffer, 0, lidos);
                    }
                    conteudoJson = new String(saida.toByteArray(), StandardCharsets.UTF_8);
                }
                return obterRepositorio().importarDadosJson(adminId, conteudoJson);
            }
        }, new CallbackResultado<String>() {
            @Override
            public void aoConcluir(String relatorioImportacao) {
                exibirDialogoTexto("Resultado da importação", relatorioImportacao);
                atualizarDadosTela(null);
            }
        });
    }
}
