package br.com.advr;

public class Movimentacao {
    public long id;
    public long processoId;
    public String fonte;
    public String titulo;
    public String textoOriginal;
    public String traducaoJuridica;
    public String statusModeracao;
    public String dataMovimentacao;
    public String liberadoPor;
    public String liberadoEm;

    public boolean estaLiberada() {
        return "LIBERADO".equals(statusModeracao);
    }
}
