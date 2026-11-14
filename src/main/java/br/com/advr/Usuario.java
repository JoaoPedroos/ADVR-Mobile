package br.com.advr;

public class Usuario {
    public long id;
    public String nome;
    public String email;
    public String cpf;
    public String telefone;
    public String perfil;

    public boolean ehAdministrador() {
        return "ADMINISTRADOR".equals(perfil);
    }

    @Override
    public String toString() {
        return nome + " • " + cpf + " • " + perfil;
    }
}
