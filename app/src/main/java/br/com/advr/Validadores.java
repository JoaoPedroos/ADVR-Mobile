package br.com.advr;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Validadores {

    private Validadores() {
    }

    public static String apenasDigitos(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replaceAll("[^0-9]", "");
    }

    public static String normalizarEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static String validarNome(String nome) {
        if (nome == null || nome.trim().length() < 3) {
            return "Informe um nome com pelo menos 3 caracteres.";
        }
        return null;
    }

    public static String validarEmail(String email) {
        if (email == null) {
            return "Informe um e-mail válido.";
        }
        boolean formatoValido = Pattern.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+", email.trim());
        if (!formatoValido) {
            return "Informe um e-mail válido.";
        }
        return null;
    }

    public static String validarSenha(String senha) {
        if (senha == null || senha.length() < 8) {
            return "A senha deve ter pelo menos 8 caracteres.";
        }
        return null;
    }

    public static boolean cpfValido(String valor) {
        String digitos = apenasDigitos(valor);
        if (digitos.length() != 11 || digitos.matches("(\\d)\\1{10}")) {
            return false;
        }
        for (int fim = 9; fim <= 10; fim++) {
            int soma = 0;
            for (int i = 0; i < fim; i++) {
                int numero = digitos.charAt(i) - '0';
                soma += numero * (fim + 1 - i);
            }
            int resto = (soma * 10) % 11;
            if (resto == 10) {
                resto = 0;
            }
            int digitoVerificador = digitos.charAt(fim) - '0';
            if (resto != digitoVerificador) {
                return false;
            }
        }
        return true;
    }
}
