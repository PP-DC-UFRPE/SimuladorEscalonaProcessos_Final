import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Representa um núcleo de CPU (Core) que executa processos em paralelo.
 * Implementação da concorrência ajustada para sincronizar 1 ciclo de CPU com 1
 * tick do simulador.
 */
public class CPUCore implements Runnable {

    private final int coreId;
    private final BlockingQueue<Processo> filaDeProntos;
    private final SimuladorEscalonamento simulador;
    private final String algoritmo;
    private volatile boolean running = true;

    public CPUCore(int coreId, SimuladorEscalonamento simulador, BlockingQueue<Processo> filaDeProntos,
            String algoritmo) {
        this.coreId = coreId;
        this.simulador = simulador;
        this.filaDeProntos = filaDeProntos;
        this.algoritmo = algoritmo;
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        // Core slot: guarda o processo que está atualmente em execução, impedindo que
        // outro Core o pegue.
        Processo processoAtual = null;

        try {
            while (running) {

                // 1. TENTA ADQUIRIR PROCESSO (se o slot do Core estiver vazio)
                if (processoAtual == null) {
                    // Tenta pegar um processo da fila, esperando um pouco
                    processoAtual = filaDeProntos.poll(5, TimeUnit.MILLISECONDS);

                    if (processoAtual == null) {
                        // Loga a inatividade dentro do tick
                        simulador.recordCoreAction(simulador.getTempoAtual(), coreId, "OCIOSO", "ESPERA_NA_FILA",
                                "Fila Vazia");
                        // Sincronização: espera o tempo de 1 Tick (20ms) antes de tentar pegar de novo.
                        Thread.sleep(SimuladorEscalonamento.SCHEDULER_PERIOD_MS);
                        continue;
                    }

                    if (processoAtual.tempoInicio == -1) {
                        processoAtual.tempoInicio = simulador.getTempoAtual();
                    }
                }

                // Processo agora está alocado no Core (processoAtual != null)

                // --- INÍCIO DA EXECUÇÃO (1 CICLO DE CPU POR TICK) ---

                // 1. Loga a execução no início do ciclo
                simulador.recordCoreAction(simulador.getTempoAtual(), coreId, processoAtual.nome, "EXECUÇÃO",
                        "Restante antes: " + processoAtual.tempoCPURestante.get());

                // 2. Executa 1 Ciclo de CPU
                processoAtual.tempoCPURestante.decrementAndGet();
                processoAtual.tempoDecorridoNoQuantum++;
                int restanteAposExecucao = processoAtual.tempoCPURestante.get();

                // 3. Verifica a Conclusão
                if (restanteAposExecucao <= 0) {
                    processoAtual.tempoTermino = simulador.getTempoAtual();
                    simulador.marcarProcessoConcluido(processoAtual);
                    processoAtual.tempoDecorridoNoQuantum = 0;
                    simulador.recordCoreAction(simulador.getTempoAtual(), coreId, processoAtual.nome, "CONCLUIDO",
                            "Término em T=" + processoAtual.tempoTermino);
                    System.out.println(
                            "✅ [T=" + simulador.getTempoAtual() + "] | P" + processoAtual.id + " (" + processoAtual.nome
                                    + ") CONCLUÍDO no Core " + coreId + ".");

                    processoAtual = null; // Core está livre
                    Thread.sleep(SimuladorEscalonamento.SCHEDULER_PERIOD_MS);
                    continue;
                }

                // 4. LÓGICA DE ESCALONAMENTO (PREEMPÇÃO)

                if ("RR".equals(algoritmo)) {
                    // Lógica para Round Robin (RR)

                    // Verifica Quantum
                    if (processoAtual.tempoDecorridoNoQuantum >= simulador.getQuantum()) {
                        // Quantum esgotado: PREEMPÇÃO
                        processoAtual.tempoDecorridoNoQuantum = 0;
                        filaDeProntos.offer(processoAtual); // Retorna para o FIM da fila
                        simulador.recordCoreAction(simulador.getTempoAtual(), coreId, processoAtual.nome, "PREEMPCÃO",
                                "Motivo: Quantum Esgotado. Restante: " + processoAtual.tempoCPURestante.get());
                        System.out.println("⏳ [T=" + simulador.getTempoAtual() + "] | P" + processoAtual.id + " ("
                                + processoAtual.nome
                                + ") preemptado no Core " + coreId
                                + ". MOTIVO: Esgotou o Quantum. Enviado para o FIM da fila.");
                        processoAtual = null; // Core está livre para pegar o próximo no próximo tick
                    }
                    // CRÍTICO: SE O QUANTUM NÃO ESGOTOU, O PROCESSO PERMANECE NO CORE
                    // (processoAtual != null)
                    // Ele será processado novamente na próxima iteração do loop (próximo tick)
                    // sem passar pela fila.

                } else if ("PP".equals(algoritmo)) {
                    // Lógica para Prioridade Preemptiva (PP)
                    Processo melhor = simulador.peekMelhorPrioridade();

                    // Verifica Preempção por Prioridade
                    if (melhor != null && melhor.prioridade < processoAtual.prioridade) {
                        filaDeProntos.offer(processoAtual);
                        simulador.recordCoreAction(simulador.getTempoAtual(), coreId, processoAtual.nome, "PREEMPCÃO",
                                "Motivo: Maior Prioridade (" + melhor.nome + "). Restante: "
                                        + processoAtual.tempoCPURestante.get());
                        System.out.println("⚠️ [T=" + simulador.getTempoAtual() + "] | P" + processoAtual.id + " ("
                                + processoAtual.nome
                                + ") preemptado no Core " + coreId + ". MOTIVO: Existe processo (" + melhor.nome
                                + ", Pri:" + melhor.prioridade + ") com PRIORIDADE MAIOR na fila. Retorna à fila.");
                        processoAtual = null; // Core está livre
                    } else {
                        // Se não for preemptado, DEVE retornar para a fila de prioridade
                        // para que o próximo Core a ficar livre o pegue, ou ele mesmo seja re-avaliado
                        // no próximo tick.
                        filaDeProntos.offer(processoAtual);
                        processoAtual = null; // Core está livre
                    }
                }

                // --- SINCRONIZAÇÃO DE TICK ---
                // Força o Core a esperar o tempo de 1 Tick (20ms) antes de recomeçar o loop.
                Thread.sleep(SimuladorEscalonamento.SCHEDULER_PERIOD_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}