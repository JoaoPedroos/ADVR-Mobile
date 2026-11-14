package br.com.advr;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Repositorio {

    private static final String SQL_SELECIONAR_PROCESSOS =
            "SELECT p.id, p.numero, p.titulo, p.descricao, p.status, p.advogado_id, coalesce(a.nome, 'Sem responsável') "
            + "FROM processos p LEFT JOIN usuarios a ON a.id = p.advogado_id";

    private static final String SQL_PERMISSAO_PROCESSO =
            "(?='ADMINISTRADOR' OR (?='ADVOGADO' AND p.advogado_id=?) "
            + "OR (?='CLIENTE' AND EXISTS(SELECT 1 FROM processo_clientes pc WHERE pc.processo_id=p.id AND pc.cliente_id=?)))";

    private final BancoDadosHelper bancoHelper;

    public Repositorio(Context context) {
        this.bancoHelper = new BancoDadosHelper(context);
    }

    private SQLiteDatabase obterBanco() {
        return bancoHelper.getWritableDatabase();
    }

    public boolean precisaConfiguracaoInicial() {
        try (Cursor cursor = obterBanco().rawQuery("SELECT count(*) FROM usuarios", null)) {
            cursor.moveToFirst();
            return cursor.getInt(0) == 0;
        }
    }

    private Usuario converterCursorParaUsuario(Cursor cursor) {
        Usuario usuario = new Usuario();
        usuario.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        usuario.nome = cursor.getString(cursor.getColumnIndexOrThrow("nome"));
        usuario.email = cursor.getString(cursor.getColumnIndexOrThrow("email"));
        usuario.cpf = cursor.getString(cursor.getColumnIndexOrThrow("cpf"));
        usuario.telefone = cursor.getString(cursor.getColumnIndexOrThrow("telefone"));
        usuario.perfil = cursor.getString(cursor.getColumnIndexOrThrow("perfil"));
        return usuario;
    }

    public Usuario buscarUsuarioPorId(long id) {
        try (Cursor cursor = obterBanco().rawQuery("SELECT * FROM usuarios WHERE id = ?", new String[]{String.valueOf(id)})) {
            if (!cursor.moveToFirst()) {
                throw new IllegalArgumentException("Sua sessão expirou. Entre novamente.");
            }
            return converterCursorParaUsuario(cursor);
        }
    }

    private void verificarPermissaoAdministrador(long usuarioId) {
        Usuario usuario = buscarUsuarioPorId(usuarioId);
        if (!usuario.ehAdministrador()) {
            throw new IllegalArgumentException("Acesso permitido apenas ao administrador.");
        }
    }

    public long cadastrarUsuario(String nome, String email, String cpf, String senha, boolean primeiroAcesso) {
        String erro = Validadores.validarNome(nome);
        if (erro == null) {
            erro = Validadores.validarEmail(email);
        }
        if (erro == null && !Validadores.cpfValido(cpf)) {
            erro = "Informe um CPF válido.";
        }
        if (erro == null) {
            erro = Validadores.validarSenha(senha);
        }
        if (erro != null) {
            throw new IllegalArgumentException(erro);
        }

        ContentValues valores = new ContentValues();
        valores.put("nome", nome.trim());
        valores.put("email", Validadores.normalizarEmail(email));
        valores.put("cpf", Validadores.apenasDigitos(cpf));
        valores.put("senha_hash", CriptografiaSenha.gerarHash(senha));
        if (primeiroAcesso) {
            valores.put("perfil", "ADMINISTRADOR");
        } else {
            valores.put("perfil", "CLIENTE");
        }

        SQLiteDatabase db = obterBanco();
        db.beginTransaction();
        try {
            if (primeiroAcesso && !precisaConfiguracaoInicial()) {
                throw new IllegalArgumentException("O administrador inicial já foi criado.");
            }
            if (!primeiroAcesso && precisaConfiguracaoInicial()) {
                throw new IllegalArgumentException("Crie primeiro o administrador inicial.");
            }
            long novoId = db.insertOrThrow("usuarios", null, valores);
            String descricaoAuditoria = primeiroAcesso ? "Administrador inicial" : "Novo cliente";
            registrarAuditoria(novoId, "CADASTRO", "usuarios", novoId, descricaoAuditoria);
            db.setTransactionSuccessful();
            return novoId;
        } catch (SQLiteConstraintException e) {
            throw new IllegalArgumentException("Este email/CPF já está em uso.");
        } finally {
            db.endTransaction();
        }
    }

    public long autenticarUsuario(String email, String senha) {
        SQLiteDatabase db = obterBanco();
        long id;
        long bloqueadoAte;
        int tentativas;
        String hashSalvo;

        try (Cursor cursor = db.rawQuery(
                "SELECT id, senha_hash, tentativas_login, bloqueado_ate FROM usuarios WHERE email = ?",
                new String[]{Validadores.normalizarEmail(email)})) {
            if (!cursor.moveToFirst()) {
                throw new IllegalArgumentException("Email ou senha incorretos.");
            }
            id = cursor.getLong(0);
            hashSalvo = cursor.getString(1);
            tentativas = cursor.getInt(2);
            bloqueadoAte = cursor.getLong(3);
        }

        long agora = System.currentTimeMillis();
        if (bloqueadoAte > agora) {
            long minutosRestantes = Math.max(1, (bloqueadoAte - agora + 59999) / 60000);
            throw new IllegalArgumentException("Conta bloqueada. Tente novamente em " + minutosRestantes + " minuto(s).");
        }
        if (bloqueadoAte != 0) {
            tentativas = 0;
        }

        ContentValues valores = new ContentValues();
        if (!CriptografiaSenha.verificarSenha(senha, hashSalvo)) {
            tentativas++;
            if (tentativas >= 5) {
                valores.put("tentativas_login", 0);
                valores.put("bloqueado_ate", agora + 300000);
                db.update("usuarios", valores, "id = ?", new String[]{String.valueOf(id)});
                throw new IllegalArgumentException("Conta bloqueada por 5 minutos após 5 erros seguidos.");
            } else {
                valores.put("tentativas_login", tentativas);
                valores.put("bloqueado_ate", 0);
                db.update("usuarios", valores, "id = ?", new String[]{String.valueOf(id)});
                throw new IllegalArgumentException("Email ou senha incorretos.");
            }
        }

        valores.put("tentativas_login", 0);
        valores.put("bloqueado_ate", 0);
        db.update("usuarios", valores, "id = ?", new String[]{String.valueOf(id)});
        return id;
    }

    public void atualizarPerfilUsuario(long usuarioId, String nome, String telefone) {
        buscarUsuarioPorId(usuarioId);
        String erro = Validadores.validarNome(nome);
        if (erro != null) {
            throw new IllegalArgumentException(erro);
        }
        ContentValues valores = new ContentValues();
        valores.put("nome", nome.trim());
        valores.put("telefone", telefone.trim());
        obterBanco().update("usuarios", valores, "id = ?", new String[]{String.valueOf(usuarioId)});
    }

    public void alterarSenhaUsuario(long usuarioId, String senhaAtual, String novaSenha) {
        buscarUsuarioPorId(usuarioId);
        String erro = Validadores.validarSenha(novaSenha);
        if (erro != null) {
            throw new IllegalArgumentException(erro);
        }
        try (Cursor cursor = obterBanco().rawQuery("SELECT senha_hash FROM usuarios WHERE id = ?", new String[]{String.valueOf(usuarioId)})) {
            cursor.moveToFirst();
            String hashAtual = cursor.getString(0);
            if (!CriptografiaSenha.verificarSenha(senhaAtual, hashAtual)) {
                throw new IllegalArgumentException("Senha atual incorreta.");
            }
        }
        ContentValues valores = new ContentValues();
        valores.put("senha_hash", CriptografiaSenha.gerarHash(novaSenha));
        obterBanco().update("usuarios", valores, "id = ?", new String[]{String.valueOf(usuarioId)});
    }

    public List<Usuario> listarUsuarios(long adminId, String filtroTexto, String filtroPerfil) {
        verificarPermissaoAdministrador(adminId);
        List<Usuario> lista = new ArrayList<>();
        String termo = "%" + filtroTexto.trim() + "%";
        String sql = "SELECT * FROM usuarios WHERE (nome LIKE ? OR cpf LIKE ?) AND (?='' OR perfil=?) ORDER BY nome, id";

        try (Cursor cursor = obterBanco().rawQuery(sql, new String[]{termo, termo, filtroPerfil, filtroPerfil})) {
            while (cursor.moveToNext()) {
                lista.add(converterCursorParaUsuario(cursor));
            }
        }
        return lista;
    }

    public void alterarPerfilAcesso(long adminId, long usuarioAlvoId, String novoPerfil) {
        verificarPermissaoAdministrador(adminId);
        if (!novoPerfil.equals("CLIENTE") && !novoPerfil.equals("ADVOGADO") && !novoPerfil.equals("ADMINISTRADOR")) {
            throw new IllegalArgumentException("Perfil inválido.");
        }

        SQLiteDatabase db = obterBanco();
        db.beginTransaction();
        try {
            Usuario usuarioAlvo = buscarUsuarioPorId(usuarioAlvoId);
            if (adminId == usuarioAlvoId && !novoPerfil.equals("ADMINISTRADOR")) {
                throw new IllegalArgumentException("O administrador não pode retirar a própria permissão.");
            }
            if (!usuarioAlvo.perfil.equals(novoPerfil)) {
                boolean possuiVinculoCliente = existeRegistro("SELECT 1 FROM processo_clientes WHERE cliente_id = ?", usuarioAlvoId);
                boolean possuiVinculoAdvogado = existeRegistro("SELECT 1 FROM processos WHERE advogado_id = ?", usuarioAlvoId);
                if (possuiVinculoCliente || possuiVinculoAdvogado) {
                    throw new IllegalArgumentException("Remova ou transfira os vínculos dos processos antes de mudar este perfil.");
                }
                ContentValues valores = new ContentValues();
                valores.put("perfil", novoPerfil);
                db.update("usuarios", valores, "id = ?", new String[]{String.valueOf(usuarioAlvoId)});
                registrarAuditoria(adminId, "ALTERAR_PERFIL", "usuarios", usuarioAlvoId, usuarioAlvo.perfil + " -> " + novoPerfil);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private boolean existeRegistro(String sql, long id) {
        try (Cursor cursor = obterBanco().rawQuery(sql, new String[]{String.valueOf(id)})) {
            return cursor.moveToFirst();
        }
    }

    private Processo converterCursorParaProcesso(Cursor cursor) {
        Processo processo = new Processo();
        processo.id = cursor.getLong(0);
        processo.numero = cursor.getString(1);
        processo.titulo = cursor.getString(2);
        processo.descricao = cursor.getString(3);
        processo.status = cursor.getString(4);
        processo.advogadoId = cursor.isNull(5) ? -1 : cursor.getLong(5);
        processo.advogado = cursor.getString(6);
        return processo;
    }

    private String[] montarParametrosEscopo(Usuario usuario) {
        String idTexto = String.valueOf(usuario.id);
        return new String[]{usuario.perfil, usuario.perfil, idTexto, usuario.perfil, idTexto};
    }

    public List<Processo> listarProcessos(long usuarioId) {
        Usuario usuario = buscarUsuarioPorId(usuarioId);
        List<Processo> lista = new ArrayList<>();
        String sql = SQL_SELECIONAR_PROCESSOS + " WHERE " + SQL_PERMISSAO_PROCESSO + " ORDER BY p.id DESC";

        try (Cursor cursor = obterBanco().rawQuery(sql, montarParametrosEscopo(usuario))) {
            while (cursor.moveToNext()) {
                lista.add(converterCursorParaProcesso(cursor));
            }
        }
        return lista;
    }

    public Processo buscarDetalhesProcesso(long usuarioId, long processoId) {
        Usuario usuario = buscarUsuarioPorId(usuarioId);
        String[] escopo = montarParametrosEscopo(usuario);
        String[] argumentos = {escopo[0], escopo[1], escopo[2], escopo[3], escopo[4], String.valueOf(processoId)};
        String sql = SQL_SELECIONAR_PROCESSOS + " WHERE " + SQL_PERMISSAO_PROCESSO + " AND p.id = ?";

        try (Cursor cursor = obterBanco().rawQuery(sql, argumentos)) {
            if (!cursor.moveToFirst()) {
                throw new IllegalArgumentException("Você não tem permissão para acessar este processo.");
            }
            return converterCursorParaProcesso(cursor);
        }
    }

    public List<Movimentacao> listarMovimentacoes(long usuarioId, long processoId) {
        Usuario usuario = buscarUsuarioPorId(usuarioId);
        buscarDetalhesProcesso(usuarioId, processoId);

        List<Movimentacao> lista = new ArrayList<>();
        String filtroCliente = "CLIENTE".equals(usuario.perfil) ? " AND m.status_moderacao = 'LIBERADO'" : "";
        String sql = "SELECT m.id, m.processo_id, m.fonte, m.titulo, m.texto_original, m.traducao_juridica, "
                + "m.status_moderacao, m.data_movimentacao, coalesce(rel.nome, ''), coalesce(m.liberado_em, '') "
                + "FROM movimentacoes m LEFT JOIN usuarios rel ON rel.id = m.liberado_por "
                + "WHERE m.processo_id = ?" + filtroCliente + " ORDER BY m.data_movimentacao DESC, m.id DESC";

        try (Cursor cursor = obterBanco().rawQuery(sql, new String[]{String.valueOf(processoId)})) {
            while (cursor.moveToNext()) {
                Movimentacao mov = new Movimentacao();
                mov.id = cursor.getLong(0);
                mov.processoId = cursor.getLong(1);
                mov.fonte = cursor.getString(2);
                mov.titulo = cursor.getString(3);
                mov.textoOriginal = cursor.getString(4);
                mov.traducaoJuridica = cursor.getString(5);
                mov.statusModeracao = cursor.getString(6);
                mov.dataMovimentacao = cursor.getString(7);
                mov.liberadoPor = cursor.getString(8);
                mov.liberadoEm = cursor.getString(9);
                lista.add(mov);
            }
        }
        return lista;
    }

    public long simularCapturaMovimentacao(long usuarioId, long processoId) {
        Usuario usuario = buscarUsuarioPorId(usuarioId);
        if ("CLIENTE".equals(usuario.perfil)) {
            throw new IllegalArgumentException("Apenas advogados ou administradores podem capturar movimentações.");
        }
        buscarDetalhesProcesso(usuarioId, processoId);

        int quantidade = 0;
        try (Cursor cursor = obterBanco().rawQuery("SELECT count(*) FROM movimentacoes WHERE processo_id = ?", new String[]{String.valueOf(processoId)})) {
            if (cursor.moveToFirst()) {
                quantidade = cursor.getInt(0);
            }
        }

        boolean ehPar = (quantidade % 2 == 0);
        String fonte = ehPar ? "DJEN" : "WEBJUR";
        String titulo = ehPar
                ? "Intimação para manifestação sobre documentos"
                : "Conclusão ao juiz para despacho";
        String textoOriginal = ehPar
                ? "Intime-se a parte interessada para se manifestar acerca dos documentos juntados aos autos, no prazo de 15 dias úteis."
                : "Autos conclusos ao magistrado titular da vara para apreciação dos pedidos formulados pelas partes.";

        SQLiteDatabase db = obterBanco();
        db.beginTransaction();
        try {
            ContentValues valores = new ContentValues();
            valores.put("processo_id", processoId);
            valores.put("fonte", fonte);
            valores.put("titulo", titulo);
            valores.put("texto_original", textoOriginal);
            valores.put("traducao_juridica", "");
            valores.put("status_moderacao", "PENDENTE");
            valores.put("data_movimentacao", dataHoraAtualUtc());

            long movimentacaoId = db.insertOrThrow("movimentacoes", null, valores);
            registrarAuditoria(usuarioId, "CAPTURAR_MOVIMENTACAO", "movimentacoes", movimentacaoId, fonte + " em processo #" + processoId);
            db.setTransactionSuccessful();
            return movimentacaoId;
        } finally {
            db.endTransaction();
        }
    }

    public void liberarMovimentacaoParaCliente(long usuarioId, long movimentacaoId, String explicacaoSimples) {
        Usuario usuario = buscarUsuarioPorId(usuarioId);
        if ("CLIENTE".equals(usuario.perfil)) {
            throw new IllegalArgumentException("Apenas advogados ou administradores podem liberar movimentações.");
        }
        if (explicacaoSimples == null || explicacaoSimples.trim().isEmpty()) {
            throw new IllegalArgumentException("Escreva a explicação em linguagem simples antes de liberar.");
        }

        long processoId = -1;
        try (Cursor cursor = obterBanco().rawQuery("SELECT processo_id FROM movimentacoes WHERE id = ?", new String[]{String.valueOf(movimentacaoId)})) {
            if (!cursor.moveToFirst()) {
                throw new IllegalArgumentException("Movimentação não encontrada.");
            }
            processoId = cursor.getLong(0);
        }

        buscarDetalhesProcesso(usuarioId, processoId);

        SQLiteDatabase db = obterBanco();
        db.beginTransaction();
        try {
            String agora = dataHoraAtualUtc();
            ContentValues valoresMov = new ContentValues();
            valoresMov.put("traducao_juridica", explicacaoSimples.trim());
            valoresMov.put("status_moderacao", "LIBERADO");
            valoresMov.put("liberado_por", usuarioId);
            valoresMov.put("liberado_em", agora);
            db.update("movimentacoes", valoresMov, "id = ?", new String[]{String.valueOf(movimentacaoId)});

            ContentValues valoresProc = new ContentValues();
            valoresProc.put("atualizado_em", agora);
            db.update("processos", valoresProc, "id = ?", new String[]{String.valueOf(processoId)});

            registrarAuditoria(usuarioId, "LIBERAR_MOVIMENTACAO", "movimentacoes", movimentacaoId, "processo_id=" + processoId);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private long buscarClientePorCpf(String cpf) {
        if (!Validadores.cpfValido(cpf)) {
            throw new IllegalArgumentException("Informe um CPF válido para o cliente.");
        }
        String cpfLimpo = Validadores.apenasDigitos(cpf);
        try (Cursor cursor = obterBanco().rawQuery("SELECT id, perfil FROM usuarios WHERE cpf = ?", new String[]{cpfLimpo})) {
            if (!cursor.moveToFirst()) {
                throw new IllegalArgumentException("CPF inexistente: " + cpfLimpo);
            }
            if (!"CLIENTE".equals(cursor.getString(1))) {
                throw new IllegalArgumentException("O CPF informado não pertence a um Cliente.");
            }
            return cursor.getLong(0);
        }
    }

    public long salvarProcesso(long adminId, long processoId, String numero, String titulo,
                               String descricao, String status, String cpfCliente, long advogadoId) {
        verificarPermissaoAdministrador(adminId);
        if (numero.trim().isEmpty() || titulo.trim().isEmpty()) {
            throw new IllegalArgumentException("Informe o número e o título do processo.");
        }

        SQLiteDatabase db = obterBanco();
        db.beginTransaction();
        try {
            if (processoId < 0 && cpfCliente.trim().isEmpty()) {
                throw new IllegalArgumentException("Informe o CPF do cliente para o novo processo.");
            }
            long clienteId = cpfCliente.trim().isEmpty() ? -1 : buscarClientePorCpf(cpfCliente);
            if (advogadoId > 0 && !"ADVOGADO".equals(buscarUsuarioPorId(advogadoId).perfil)) {
                throw new IllegalArgumentException("Selecione um advogado válido.");
            }

            String estadoAnterior = processoId > 0 ? buscarDetalhesProcesso(adminId, processoId).toString() : "Novo processo";
            String statusFinal = status.trim().isEmpty() ? "Em andamento" : status.trim();

            ContentValues valores = new ContentValues();
            valores.put("numero", numero.trim());
            valores.put("titulo", titulo.trim());
            valores.put("descricao", descricao.trim());
            valores.put("status", statusFinal);
            if (advogadoId > 0) {
                valores.put("advogado_id", advogadoId);
            } else {
                valores.putNull("advogado_id");
            }
            valores.put("atualizado_em", dataHoraAtualUtc());

            if (processoId > 0) {
                db.update("processos", valores, "id = ?", new String[]{String.valueOf(processoId)});
            } else {
                processoId = db.insertOrThrow("processos", null, valores);
            }

            if (clienteId > 0) {
                ContentValues vinculo = new ContentValues();
                vinculo.put("processo_id", processoId);
                vinculo.put("cliente_id", clienteId);
                db.insertWithOnConflict("processo_clientes", null, vinculo, SQLiteDatabase.CONFLICT_IGNORE);
            }

            registrarAuditoria(adminId, "SALVAR_PROCESSO", "processos", processoId,
                    estadoAnterior + " -> " + numero.trim() + "; status=" + statusFinal + "; advogado_id=" + advogadoId + "; cliente_id=" + clienteId);
            db.setTransactionSuccessful();
            return processoId;
        } catch (SQLiteConstraintException e) {
            throw new IllegalArgumentException("Já existe outro processo com este número.");
        } finally {
            db.endTransaction();
        }
    }

    public List<Usuario> listarClientesVinculados(long adminId, long processoId) {
        verificarPermissaoAdministrador(adminId);
        buscarDetalhesProcesso(adminId, processoId);

        List<Usuario> lista = new ArrayList<>();
        String sql = "SELECT u.* FROM usuarios u JOIN processo_clientes pc ON pc.cliente_id = u.id WHERE pc.processo_id = ? ORDER BY u.nome";
        try (Cursor cursor = obterBanco().rawQuery(sql, new String[]{String.valueOf(processoId)})) {
            while (cursor.moveToNext()) {
                lista.add(converterCursorParaUsuario(cursor));
            }
        }
        return lista;
    }

    public void removerVinculoCliente(long adminId, long processoId, long clienteId) {
        verificarPermissaoAdministrador(adminId);
        buscarDetalhesProcesso(adminId, processoId);

        SQLiteDatabase db = obterBanco();
        db.beginTransaction();
        try {
            int removidos = db.delete("processo_clientes", "processo_id = ? AND cliente_id = ?",
                    new String[]{String.valueOf(processoId), String.valueOf(clienteId)});
            if (removidos > 0) {
                registrarAuditoria(adminId, "REMOVER_VINCULO", "processos", processoId, "cliente_id=" + clienteId);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public int[] obterEstatisticasPainel(long adminId) {
        verificarPermissaoAdministrador(adminId);
        try (Cursor cursor = obterBanco().rawQuery(
                "SELECT count(*), coalesce(sum(CASE WHEN perfil='ADVOGADO' THEN 1 ELSE 0 END), 0) FROM usuarios", null)) {
            cursor.moveToFirst();
            return new int[]{cursor.getInt(0), cursor.getInt(1)};
        }
    }

    public String gerarRelatorioAuditoria(long adminId) {
        verificarPermissaoAdministrador(adminId);
        StringBuilder relatorio = new StringBuilder();
        String sql = "SELECT a.criado_em, u.nome, a.acao, a.entidade, a.entidade_id, a.detalhes "
                + "FROM auditoria a JOIN usuarios u ON u.id = a.ator_id ORDER BY a.id DESC LIMIT 100";

        try (Cursor cursor = obterBanco().rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                relatorio.append(cursor.getString(0)).append(" • ").append(cursor.getString(1)).append("\n")
                        .append(cursor.getString(2)).append(" / ").append(cursor.getString(3)).append(" #").append(cursor.getLong(4)).append("\n")
                        .append(cursor.getString(5)).append("\n\n");
            }
        }
        if (relatorio.length() == 0) {
            return "Nenhum registro.";
        }
        return relatorio.toString();
    }

    public String importarDadosJson(long adminId, String textoJson) {
        verificarPermissaoAdministrador(adminId);
        JSONArray listaUsuariosJson = new JSONArray();
        JSONArray listaProcessosJson;

        try {
            String conteudoLimpo = textoJson.trim();
            if (conteudoLimpo.startsWith("{")) {
                JSONObject raiz = new JSONObject(conteudoLimpo);
                if (raiz.has("usuarios")) {
                    listaUsuariosJson = raiz.getJSONArray("usuarios");
                }
                listaProcessosJson = raiz.has("processos") ? raiz.getJSONArray("processos") : new JSONArray();
            } else {
                listaProcessosJson = new JSONArray(conteudoLimpo);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON inválido: use uma lista de processos ou um objeto com 'usuarios' e 'processos'.");
        }

        if (listaUsuariosJson.length() + listaProcessosJson.length() > 1000) {
            throw new IllegalArgumentException("Importe no máximo 1.000 registros por arquivo.");
        }

        StringBuilder erros = new StringBuilder();
        int usuariosImportados = 0;
        int processosImportados = 0;
        Map<String, String> cacheHashes = new HashMap<>();

        for (int i = 0; i < listaUsuariosJson.length(); i++) {
            try {
                JSONObject itemUsuario = listaUsuariosJson.getJSONObject(i);
                String nome = lerCampoJson(itemUsuario, "nome", "Nome");
                String email = lerCampoJson(itemUsuario, "email", "E-mail");
                String cpf = lerCampoJson(itemUsuario, "cpf", "CPF");
                String telefone = lerCampoJson(itemUsuario, "telefone", "Telefone");
                String perfil = lerCampoJson(itemUsuario, "perfil", "Perfil").toUpperCase(Locale.ROOT);
                if (perfil.isEmpty()) {
                    perfil = "CLIENTE";
                }
                String senha = itemUsuario.optString("senha", "Teste123!");

                String erroValidacao = Validadores.validarNome(nome);
                if (erroValidacao == null) {
                    erroValidacao = Validadores.validarEmail(email);
                }
                if (erroValidacao == null && !Validadores.cpfValido(cpf)) {
                    erroValidacao = "Informe um CPF válido.";
                }
                if (erroValidacao == null) {
                    erroValidacao = Validadores.validarSenha(senha);
                }
                if (erroValidacao == null && !perfil.equals("CLIENTE") && !perfil.equals("ADVOGADO")) {
                    erroValidacao = "Perfil inválido na importação (use CLIENTE ou ADVOGADO).";
                }
                if (erroValidacao != null) {
                    throw new IllegalArgumentException(erroValidacao);
                }

                String cpfLimpo = Validadores.apenasDigitos(cpf);
                String emailLimpo = Validadores.normalizarEmail(email);
                boolean jaExiste;
                try (Cursor cursor = obterBanco().rawQuery("SELECT 1 FROM usuarios WHERE cpf = ? OR email = ?", new String[]{cpfLimpo, emailLimpo})) {
                    jaExiste = cursor.moveToFirst();
                }
                if (jaExiste) {
                    usuariosImportados++;
                    continue;
                }

                String hash = cacheHashes.get(senha);
                if (hash == null) {
                    hash = CriptografiaSenha.gerarHash(senha);
                    cacheHashes.put(senha, hash);
                }

                ContentValues valores = new ContentValues();
                valores.put("nome", nome.trim());
                valores.put("email", emailLimpo);
                valores.put("cpf", cpfLimpo);
                valores.put("telefone", telefone.trim());
                valores.put("senha_hash", hash);
                valores.put("perfil", perfil);

                long novoUsuarioId = obterBanco().insertOrThrow("usuarios", null, valores);
                registrarAuditoria(adminId, "IMPORTAR_USUARIO", "usuarios", novoUsuarioId, nome.trim() + " (" + perfil + ")");
                usuariosImportados++;
            } catch (Exception e) {
                erros.append("Usuário ").append(i + 1).append(": ").append(e.getMessage()).append("\n");
            }
        }

        for (int i = 0; i < listaProcessosJson.length(); i++) {
            try {
                JSONObject itemProcesso = listaProcessosJson.getJSONObject(i);
                String cpf = lerCampoJson(itemProcesso, "cpf", "CPF");
                String numero = lerCampoJson(itemProcesso, "numero_processo", "Número do Processo");
                String nomeAdvogado = lerCampoJson(itemProcesso, "nome_advogado", "Nome do Advogado");

                buscarClientePorCpf(cpf);
                if (numero.isEmpty()) {
                    throw new IllegalArgumentException("Número do processo obrigatório.");
                }

                long advogadoId = -1;
                if (!nomeAdvogado.isEmpty()) {
                    try (Cursor cursor = obterBanco().rawQuery(
                            "SELECT id FROM usuarios WHERE perfil='ADVOGADO' AND nome=? COLLATE NOCASE",
                            new String[]{nomeAdvogado})) {
                        if (!cursor.moveToFirst()) {
                            throw new IllegalArgumentException("Advogado não encontrado: " + nomeAdvogado);
                        }
                        advogadoId = cursor.getLong(0);
                        if (cursor.moveToNext()) {
                            throw new IllegalArgumentException("Nome de advogado ambíguo: " + nomeAdvogado);
                        }
                    }
                }

                long idExistente = -1;
                try (Cursor cursor = obterBanco().rawQuery("SELECT id FROM processos WHERE numero = ?", new String[]{numero})) {
                    if (cursor.moveToFirst()) {
                        idExistente = cursor.getLong(0);
                    }
                }

                Processo processoAtual = idExistente > 0 ? buscarDetalhesProcesso(adminId, idExistente) : null;
                String titulo = itemProcesso.optString("titulo", processoAtual == null ? "Processo " + numero : processoAtual.titulo);
                String descricao = itemProcesso.optString("descricao", processoAtual == null ? "Importado de JSON" : processoAtual.descricao);
                String status = itemProcesso.optString("status", processoAtual == null ? "Em andamento" : processoAtual.status);

                long processoSalvoId = salvarProcesso(adminId, idExistente, numero, titulo, descricao, status, cpf, advogadoId);

                JSONArray movimentacoesJson = itemProcesso.optJSONArray("movimentacoes");
                if (movimentacoesJson != null) {
                    for (int j = 0; j < movimentacoesJson.length(); j++) {
                        JSONObject itemMov = movimentacoesJson.getJSONObject(j);
                        String tituloMov = itemMov.optString("titulo", "").trim();
                        String textoOrigMov = itemMov.optString("texto_original", "").trim();
                        if (tituloMov.isEmpty() || textoOrigMov.isEmpty()) {
                            continue;
                        }

                        boolean jaExisteMov;
                        try (Cursor cursorMov = obterBanco().rawQuery(
                                "SELECT 1 FROM movimentacoes WHERE processo_id = ? AND titulo = ?",
                                new String[]{String.valueOf(processoSalvoId), tituloMov})) {
                            jaExisteMov = cursorMov.moveToFirst();
                        }
                        if (jaExisteMov) {
                            continue;
                        }

                        String fonteMov = itemMov.optString("fonte", "DJEN").trim().toUpperCase(Locale.ROOT);
                        if (!fonteMov.equals("DJEN") && !fonteMov.equals("WEBJUR") && !fonteMov.equals("MANUAL")) {
                            fonteMov = "DJEN";
                        }
                        String traducaoMov = itemMov.optString("traducao_juridica", "").trim();
                        String statusMod = itemMov.optString("status_moderacao", "PENDENTE").trim().toUpperCase(Locale.ROOT);
                        boolean liberada = "LIBERADO".equals(statusMod) && !traducaoMov.isEmpty();

                        ContentValues valoresMov = new ContentValues();
                        valoresMov.put("processo_id", processoSalvoId);
                        valoresMov.put("fonte", fonteMov);
                        valoresMov.put("titulo", tituloMov);
                        valoresMov.put("texto_original", textoOrigMov);
                        valoresMov.put("traducao_juridica", traducaoMov);
                        valoresMov.put("status_moderacao", liberada ? "LIBERADO" : "PENDENTE");
                        valoresMov.put("data_movimentacao", itemMov.optString("data_movimentacao", dataHoraAtualUtc()));
                        if (liberada) {
                            valoresMov.put("liberado_por", adminId);
                            valoresMov.put("liberado_em", dataHoraAtualUtc());
                        }
                        obterBanco().insertOrThrow("movimentacoes", null, valoresMov);
                    }
                }
                processosImportados++;
            } catch (Exception e) {
                erros.append("Processo ").append(i + 1).append(": ").append(e.getMessage()).append("\n");
            }
        }

        String resumoErros = erros.length() == 0 ? "Nenhum erro." : erros.toString();
        return "Usuários importados: " + usuariosImportados + " / " + listaUsuariosJson.length()
                + "\nProcessos importados: " + processosImportados + " / " + listaProcessosJson.length()
                + "\n\n" + resumoErros;
    }

    private String lerCampoJson(JSONObject objeto, String chavePrincipal, String chaveAlternativa) {
        String valor = objeto.optString(chavePrincipal, "");
        if (valor.isEmpty()) {
            return objeto.optString(chaveAlternativa, "").trim();
        }
        return valor.trim();
    }

    private String dataHoraAtualUtc() {
        return Instant.now().toString();
    }

    private void registrarAuditoria(long atorId, String acao, String entidade, long entidadeId, String detalhes) {
        ContentValues valores = new ContentValues();
        valores.put("ator_id", atorId);
        valores.put("acao", acao);
        valores.put("entidade", entidade);
        valores.put("entidade_id", entidadeId);
        valores.put("detalhes", detalhes);
        obterBanco().insertOrThrow("auditoria", null, valores);
    }
}
