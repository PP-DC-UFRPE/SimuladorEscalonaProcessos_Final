
/**
 * CLASSE: Processo
 * * FUNÇÃO: Modelo de Dados Atômico.
 * * DESCRIÇÃO: 
 * Representa uma tarefa (processo) que requer tempo de CPU. Contém dados imutáveis 
 * (ID, Prioridade, Tempo Total de CPU) e dados de estado mutável. 
 * * CONCORRÊNCIA: 
 * Utiliza 'AtomicInteger' para variáveis críticas ('tempoCPURestante', 'tempoEspera') 
 * para garantir que as operações de leitura/escrita e decremento sejam thread-safe, 
 * prevenindo condições de corrida quando múltiplos CPUCores tentam atualizar o 
 * estado do mesmo processo simultaneamente.
 */

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Representa um processo a ser escalonado pela CPU.
 */
public class Processo {
    public final int id;
    public final String nome;
    public final int prioridade;
    public final String tipo;
    public final int tempoTotalCPU;
    public final int tempoChegada;
    public final AtomicInteger tempoCPURestante;

    public int tempoDecorridoNoQuantum = 0;
    public final AtomicInteger tempoEspera = new AtomicInteger(0);
    public int tempoInicio = -1;
    public int tempoTermino = -1;

    // Construtor com 6 argumentos: (id, nome, prioridade, tipo, tempoTotalCPU,
    // tempoChegada)
    public Processo(int id, String nome, int prioridade, String tipo, int tempoTotalCPU, int tempoChegada) {
        this.id = id;
        this.nome = nome;
        this.prioridade = prioridade;
        this.tipo = tipo;
        this.tempoTotalCPU = tempoTotalCPU;
        this.tempoChegada = tempoChegada;
        this.tempoCPURestante = new AtomicInteger(tempoTotalCPU);
    }

    public int getTempoTurnaround() {
        if (tempoTermino == -1)
            return 0;
        return tempoTermino - tempoChegada;
    }

    @Override
    public String toString() {
        return nome + "(Pri:" + prioridade + ", CPU:" + tempoCPURestante.get() + ")";
    }
}