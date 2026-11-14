package br.com.advr;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class BancoDadosHelper extends SQLiteOpenHelper {

    public static final String NOME_BANCO = "advr.db";
    public static final int VERSAO_BANCO = 2;
    private static final String HASH_SENHA_PADRAO = "600000:QURWUl9ERU1PX1NBTFQxNg==:MCrkE3s11KkZESdVPd0J5LPmtRIY594JiTCqSWtgowc=";

    private final Context contexto;

    public BancoDadosHelper(Context context) {
        super(context, NOME_BANCO, null, VERSAO_BANCO);
        this.contexto = context.getApplicationContext();
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        try (InputStream entrada = contexto.getAssets().open("banco_advr.sql");
             ByteArrayOutputStream saida = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[4096];
            int bytesLidos;
            while ((bytesLidos = entrada.read(buffer)) != -1) {
                saida.write(buffer, 0, bytesLidos);
            }

            String scriptSql = new String(saida.toByteArray(), StandardCharsets.UTF_8);
            scriptSql = scriptSql.replaceAll("(?m)^--.*$", "");

            String[] comandos = scriptSql.split(";");
            for (String comando : comandos) {
                String sqlLimpo = comando.trim();
                if (!sqlLimpo.isEmpty()) {
                    db.execSQL(sqlLimpo);
                }
            }

            inserirDadosIniciais(db);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao criar o banco SQLite.", e);
        }
    }

    private void inserirDadosIniciais(SQLiteDatabase db) {
        long adminId = inserirUsuario(db, "Administrador ADVR", "admin@advr.com", "52998224725", "(61) 99911-0001", "ADMINISTRADOR");
        long adv1Id = inserirUsuario(db, "Dr. Carlos Andrade", "carlos@advr.com", "12345678909", "(61) 99922-0002", "ADVOGADO");
        long adv2Id = inserirUsuario(db, "Dra. Ana Paula", "ana@advr.com", "98765432100", "(61) 99922-0003", "ADVOGADO");
        long cli1Id = inserirUsuario(db, "Mariana Silva", "mariana@teste.com", "11144477735", "(61) 99933-0001", "CLIENTE");
        long cli2Id = inserirUsuario(db, "João Santos", "joao@teste.com", "22255588846", "(61) 99933-0002", "CLIENTE");
        long cli3Id = inserirUsuario(db, "Lucas Oliveira", "lucas@teste.com", "33366699957", "(61) 99933-0003", "CLIENTE");
        long cli4Id = inserirUsuario(db, "Carla Souza", "carla@teste.com", "44477711107", "(61) 99933-0004", "CLIENTE");

        long p1 = inserirProcesso(db, "PROC-001", "Ação de Indenização", "Cobrança indevida em fatura e negativação irregular.", "Em andamento", adv1Id);
        long p2 = inserirProcesso(db, "PROC-002", "Ação de Obrigação de Fazer", "Cancelamento de serviço não solicitado.", "Em andamento", adv1Id);
        long p3 = inserirProcesso(db, "PROC-003", "Ação Revisional de Contrato", "Revisão de juros bancários.", "Aguardando análise", adv2Id);
        long p4 = inserirProcesso(db, "PROC-004", "Ação Trabalhista", "Pagamento de verbas rescisórias.", "Em andamento", adv2Id);
        long p5 = inserirProcesso(db, "PROC-005", "Ação Declaratória", "Análise inicial de documentação antes da distribuição.", "Triagem", -1);

        vincularCliente(db, p1, cli1Id);
        vincularCliente(db, p2, cli2Id);
        vincularCliente(db, p3, cli3Id);
        vincularCliente(db, p4, cli1Id);
        vincularCliente(db, p5, cli4Id);

        inserirMovimentacao(db, p1, "DJEN", "Despacho de citação",
                "Cite-se a parte ré para apresentar contestação no prazo legal de 15 dias úteis, sob pena de revelia.",
                "O juiz mandou avisar a empresa processada sobre a ação. Ela tem 15 dias úteis para apresentar a defesa dela.",
                "LIBERADO", "2026-11-10T14:00:00.000Z", adv1Id, "2026-11-10T15:00:00.000Z");

        inserirMovimentacao(db, p1, "WEBJUR", "Juntada de contestação",
                "Juntada de petição de contestação tempestiva pela parte requerida acompanhada de documentos.",
                "", "PENDENTE", "2026-11-13T16:30:00.000Z", -1, null);

        inserirMovimentacao(db, p2, "DJEN", "Decisão liminar deferida",
                "Defiro a tutela de urgência para determinar o imediato cancelamento das cobranças, sob pena de multa diária.",
                "O juiz aceitou o pedido urgente e ordenou que a empresa pare de cobrar imediatamente, sob pena de multa por dia de atraso.",
                "LIBERADO", "2026-11-11T10:15:00.000Z", adv1Id, "2026-11-11T11:00:00.000Z");

        inserirMovimentacao(db, p3, "DJEN", "Intimação para juntada de comprovante de renda",
                "Intime-se a parte autora para comprovar a hipossuficiência financeira em 10 dias.",
                "O juiz pediu para você enviar um comprovante de renda recente em até 10 dias para confirmar o direito à justiça gratuita.",
                "LIBERADO", "2026-11-12T09:40:00.000Z", adv2Id, "2026-11-12T10:20:00.000Z");

        inserirMovimentacao(db, p4, "WEBJUR", "Designação de audiência de conciliação",
                "Designada audiência inicial de tentativa de conciliação para o dia 05/12/2026 às 14h00.",
                "", "PENDENTE", "2026-11-13T18:10:00.000Z", -1, null);

        inserirAuditoria(db, adminId, "CARGA_INICIAL", "sistema", 1, "Dados fictícios iniciais carregados para demonstração");
    }

    private long inserirUsuario(SQLiteDatabase db, String nome, String email, String cpf, String telefone, String perfil) {
        ContentValues valores = new ContentValues();
        valores.put("nome", nome);
        valores.put("email", email);
        valores.put("cpf", cpf);
        valores.put("telefone", telefone);
        valores.put("senha_hash", HASH_SENHA_PADRAO);
        valores.put("perfil", perfil);
        return db.insertOrThrow("usuarios", null, valores);
    }

    private long inserirProcesso(SQLiteDatabase db, String numero, String titulo, String descricao, String status, long advogadoId) {
        ContentValues valores = new ContentValues();
        valores.put("numero", numero);
        valores.put("titulo", titulo);
        valores.put("descricao", descricao);
        valores.put("status", status);
        if (advogadoId > 0) {
            valores.put("advogado_id", advogadoId);
        } else {
            valores.putNull("advogado_id");
        }
        return db.insertOrThrow("processos", null, valores);
    }

    private void vincularCliente(SQLiteDatabase db, long processoId, long clienteId) {
        ContentValues valores = new ContentValues();
        valores.put("processo_id", processoId);
        valores.put("cliente_id", clienteId);
        db.insertOrThrow("processo_clientes", null, valores);
    }

    private void inserirMovimentacao(SQLiteDatabase db, long processoId, String fonte, String titulo,
                                     String textoOriginal, String traducao, String status,
                                     String dataMovimentacao, long liberadoPor, String liberadoEm) {
        ContentValues valores = new ContentValues();
        valores.put("processo_id", processoId);
        valores.put("fonte", fonte);
        valores.put("titulo", titulo);
        valores.put("texto_original", textoOriginal);
        valores.put("traducao_juridica", traducao);
        valores.put("status_moderacao", status);
        valores.put("data_movimentacao", dataMovimentacao);
        if (liberadoPor > 0) {
            valores.put("liberado_por", liberadoPor);
            valores.put("liberado_em", liberadoEm);
        }
        db.insertOrThrow("movimentacoes", null, valores);
    }

    private void inserirAuditoria(SQLiteDatabase db, long atorId, String acao, String entidade, long entidadeId, String detalhes) {
        ContentValues valores = new ContentValues();
        valores.put("ator_id", atorId);
        valores.put("acao", acao);
        valores.put("entidade", entidade);
        valores.put("entidade_id", entidadeId);
        valores.put("detalhes", detalhes);
        db.insertOrThrow("auditoria", null, valores);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS movimentacoes ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "processo_id INTEGER NOT NULL REFERENCES processos(id) ON DELETE CASCADE,"
                    + "fonte TEXT NOT NULL DEFAULT 'DJEN' CHECK(fonte IN ('DJEN','WEBJUR','MANUAL')),"
                    + "titulo TEXT NOT NULL CHECK(length(trim(titulo)) > 0),"
                    + "texto_original TEXT NOT NULL,"
                    + "traducao_juridica TEXT NOT NULL DEFAULT '',"
                    + "status_moderacao TEXT NOT NULL DEFAULT 'PENDENTE' CHECK(status_moderacao IN ('PENDENTE','LIBERADO')),"
                    + "data_movimentacao TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),"
                    + "liberado_por INTEGER REFERENCES usuarios(id) ON DELETE RESTRICT,"
                    + "liberado_em TEXT)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_movimentacoes_processo ON movimentacoes(processo_id, data_movimentacao)");
        }
    }
}
