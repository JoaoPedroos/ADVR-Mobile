package br.com.advr;

public class Processo {
    public long id;
    public long advogadoId;
    public String numero;
    public String titulo;
    public String descricao;
    public String status;
    public String advogado;

    @Override
    public String toString() {
        return numero + " — " + titulo + "\n" + status + " • " + advogado;
    }
}
