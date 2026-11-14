package br.com.advr;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class CriptografiaSenha {

    private static final int ITERACOES = 600000;

    private CriptografiaSenha() {
    }

    public static String gerarHash(String senha) {
        byte[] salt = new byte[16];
        SecureRandom random = new SecureRandom();
        random.nextBytes(salt);

        byte[] derivado = derivarChave(senha, salt, ITERACOES);
        String saltBase64 = Base64.getEncoder().encodeToString(salt);
        String hashBase64 = Base64.getEncoder().encodeToString(derivado);

        return ITERACOES + ":" + saltBase64 + ":" + hashBase64;
    }

    public static boolean verificarSenha(String senhaDigitada, String hashSalvo) {
        try {
            String[] partes = hashSalvo.split(":");
            int quantidadeIteracoes = Integer.parseInt(partes[0]);
            if (quantidadeIteracoes < 1 || quantidadeIteracoes > 2000000) {
                return false;
            }
            byte[] salt = Base64.getDecoder().decode(partes[1]);
            byte[] hashEsperado = Base64.getDecoder().decode(partes[2]);
            byte[] hashCalculado = derivarChave(senhaDigitada, salt, quantidadeIteracoes);

            return MessageDigest.isEqual(hashEsperado, hashCalculado);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] derivarChave(String senha, byte[] salt, int iteracoes) {
        char[] caracteres = senha.toCharArray();
        PBEKeySpec especificacao = new PBEKeySpec(caracteres, salt, iteracoes, 256);
        try {
            SecretKeyFactory fabrica = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return fabrica.generateSecret(especificacao).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível proteger a senha.", e);
        } finally {
            especificacao.clearPassword();
            Arrays.fill(caracteres, '\0');
        }
    }
}
