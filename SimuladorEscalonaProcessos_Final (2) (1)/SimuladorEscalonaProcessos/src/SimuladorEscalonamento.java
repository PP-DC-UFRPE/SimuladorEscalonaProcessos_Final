/**
 * CLASSE: SimuladorEscalonamento
 * * FUNÇÃO: Orquestrador, Agendador e Coletor de Métricas.
 * * DESCRIÇÃO:
 * Centraliza a lógica de controle da simulação. Gerencia o tempo lógico ('tempoAtual'),
 * coordena a inicialização e o encerramento dos Cores ('ExecutorService'), e
 * utiliza um agendador ('ScheduledExecutorService') para disparar o 'tick()'
 * periódico, que injeta novos processos e atualiza o estado da fila (LOG DINÂMICO).
 * * CONCORRÊNCIA:
 * Atua como o MONITOR principal. Garante a SINCRONIZAÇÃO do tempo (AtomicInteger)
 * e coordena a cooperação entre a thread do scheduler e as threads dos Cores através
 * da Fila de Prontos ('BlockingQueue'). Armazena o histórico de uso dos Cores.
 */

// SimuladorEscalonamento.java
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class SimuladorEscalonamento {

    // Estruturas de Dados
    private List<Processo> todosProcessos = new ArrayList<>();
    private final List<Processo> processosConcluidos = Collections.synchronizedList(new ArrayList<>());

    // LOG CENTRAL: Linha do Tempo de Eventos Detalhados
    private static class EventLogEntry {
        final int tempo;
        final int coreId;
        final String processoNome;
        final String acao;
        final String detalhes;

        public EventLogEntry(int t, int cId, String pNome, String acao, String detalhes) {
            this.tempo = t;
            this.coreId = cId;
            this.processoNome = pNome;
            this.acao = acao;
            this.detalhes = detalhes;
        }
    }

    private final List<EventLogEntry> eventTimeline = Collections.synchronizedList(new ArrayList<>());

    // Estado Atômico
    public final AtomicInteger tempoAtual = new AtomicInteger(0);
    private int quantum = 1;
    private String algoritmoSelecionado = "RR";

    // Paralelismo
    private final int NUM_CORES = 3;
    private ExecutorService executorCores;
    private List<CPUCore> workers = new ArrayList<>();
    private BlockingQueue<Processo> filaDeProntos;
    private ScheduledExecutorService scheduler;
    public static final int SCHEDULER_PERIOD_MS = 20; // CONSTANTE CRÍTICA DE SINCRONIZAÇÃO

    // Métodos de Acesso e Setup
    public SimuladorEscalonamento() {
        /* nada adicional */ }

    public void setProcessos(List<Processo> processos) {
        this.todosProcessos = new ArrayList<>(processos);
    }

    public int getQuantum() {
        return quantum;
    }

    public int getTempoAtual() {
        return tempoAtual.get();
    }

    // REGISTRA A AÇÃO DO CORE/EVENTO NOVO
    public void recordCoreAction(int t, int cId, String pNome, String acao, String detalhes) {
        eventTimeline.add(new EventLogEntry(t, cId, pNome, acao, detalhes));
    }

    // LÓGICA DE SIMULAÇÃO (mantida)
    public void executarSimulacao(String algoritmoSelecionado, int quantum) {
        if (todosProcessos == null || todosProcessos.isEmpty()) {
            System.out.println("Nenhum processo para simular.");
            return;
        }

        this.algoritmoSelecionado = algoritmoSelecionado;
        this.quantum = quantum;
        this.tempoAtual.set(0);
        processosConcluidos.clear();
        eventTimeline.clear();

        // --- Reset de estados dos processos ---
        todosProcessos.forEach(p -> {
            p.tempoCPURestante.set(p.tempoTotalCPU);
            p.tempoEspera.set(0);
            p.tempoInicio = -1;
            p.tempoTermino = -1;
            p.tempoDecorridoNoQuantum = 0;
        });

        // 1. Criar a fila apropriada
        if ("PP".equals(algoritmoSelecionado)) {
            Comparator<Processo> priorityComparator = Comparator.comparingInt(p -> p.prioridade);
            filaDeProntos = new PriorityBlockingQueue<>(10, priorityComparator);
        } else {
            filaDeProntos = new LinkedBlockingQueue<>();
        }

        // 2. Injetar processos com tempoChegada = 0
        for (Processo p : todosProcessos) {
            if (p.tempoChegada == 0) {
                filaDeProntos.offer(p);
                System.out.println("➡️ [T=0] | P" + p.id + " (" + p.nome + ", Pri:" + p.prioridade
                        + ") CHEGOU. Injetado na Fila de Prontos.");
                recordCoreAction(0, 0, p.nome, "CHEGADA", "Pri:" + p.prioridade + " | CPU:" + p.tempoTotalCPU);
            }
        }

        // Log inicial do estado da fila
        recordCoreAction(0, 0, "FILA", "ESTADO_FILA", getQueueStateString());
        logQueueState();

        // 3. Criar executor de cores e workers (Paralelismo)
        executorCores = Executors.newFixedThreadPool(NUM_CORES);
        workers.clear();
        for (int i = 1; i <= NUM_CORES; i++) {
            CPUCore worker = new CPUCore(i, this, filaDeProntos, algoritmoSelecionado);
            workers.add(worker);
            executorCores.submit(worker);
        }

        System.out.println("\n==============================================");
        System.out.println("=== Início da Simulação PARALELA: " + algoritmoSelecionado + " (Q=" + quantum + " | Cores: "
                + NUM_CORES + ") ===");
        System.out.println("==============================================");

        // 4. Agendador que incrementa o tempo (tick)
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                tick();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, SCHEDULER_PERIOD_MS, SCHEDULER_PERIOD_MS, TimeUnit.MILLISECONDS);

        waitForCompletion();
        shutdownAndAwaitTermination();
        System.out.println("\n\n--- Simulação Concluída em T=" + getTempoAtual() + " ---");
    }

    private void tick() {
        int novoTempo = tempoAtual.incrementAndGet();

        // 1. Adiciona novas chegadas à fila de prontos (T > 0)
        for (Processo p : todosProcessos) {
            if (p.tempoChegada == novoTempo && p.tempoCPURestante.get() > 0 && !filaDeProntos.contains(p)
                    && !processosConcluidos.contains(p)) {
                filaDeProntos.offer(p);
                System.out.println("➡️ [T=" + novoTempo + "] | P" + p.id + " (" + p.nome + ", Pri:" + p.prioridade
                        + ") CHEGOU. Adicionado à Fila de Prontos.");
                recordCoreAction(novoTempo, 0, p.nome, "CHEGADA", "Pri:" + p.prioridade + " | CPU:" + p.tempoTotalCPU);
            }
        }

        // 2. LOG DA FILA DINÂMICA
        recordCoreAction(novoTempo, 0, "FILA", "ESTADO_FILA", getQueueStateString());
        logQueueState();

        // 3. Incrementa o tempo de espera de TODOS os processos na fila de prontos.
        incrementarTempoEsperaDaFila(filaDeProntos);
    }

    private String getQueueStateString() {
        if (filaDeProntos.isEmpty()) {
            return "[Vazia]";
        }
        StringBuilder sb = new StringBuilder();
        // Garante que a ordem da Fila seja a visualizada no log
        List<Processo> sortedQueue = filaDeProntos.stream()
                .sorted((p1, p2) -> {
                    if ("PP".equals(algoritmoSelecionado)) {
                        return Integer.compare(p1.prioridade, p2.prioridade);
                    } else {
                        // Para RR, a ordem não é estritamente definida, mas a LinkedBlockingQueue
                        // (FIFO) é usada. Usamos o nome como desempate para garantir ordem de
                        // visualização estável.
                        return p1.nome.compareTo(p2.nome);
                    }
                })
                .collect(Collectors.toList());

        for (Processo p : sortedQueue) {
            sb.append(p.nome).append("(Pri:").append(p.prioridade).append(", CPU:").append(p.tempoCPURestante.get())
                    .append(")");
            sb.append(" -> ");
        }
        return sb.length() > 0 ? sb.substring(0, sb.length() - 4) : "[Vazia]";
    }

    private void logQueueState() {
        System.out.print("📦 Fila de Prontos (T=" + tempoAtual.get() + "): ");
        System.out.println(getQueueStateString());
    }

    private void incrementarTempoEsperaDaFila(BlockingQueue<Processo> fila) {
        for (Processo p : fila) {
            p.tempoEspera.incrementAndGet();
        }
    }

    public void marcarProcessoConcluido(Processo p) {
        processosConcluidos.add(p);
    }

    public Processo peekMelhorPrioridade() {
        if ("PP".equals(algoritmoSelecionado)) {
            return filaDeProntos.peek();
        } else {
            return null;
        }
    }

    private void waitForCompletion() {
        int total = todosProcessos.size();
        // Adiciona um timeout para evitar loop infinito em caso de erro no código do
        // Core
        long startTime = System.currentTimeMillis();
        long maxWaitTime = 5000; // 5 segundos de espera máxima

        while (processosConcluidos.size() < total && (System.currentTimeMillis() - startTime < maxWaitTime)) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void shutdownAndAwaitTermination() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        for (CPUCore w : workers) {
            w.stop();
        }

        if (executorCores != null) {
            executorCores.shutdown();
            try {
                if (!executorCores.awaitTermination(2, TimeUnit.SECONDS)) {
                    executorCores.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorCores.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // EXIBIÇÃO DE TIMELINE ORIENTADA AO TEMPO (COM CORREÇÃO DE BUG DE LOG)
    public void exibirTabelaTimeline() {
        System.out.println(
                "\n=======================================================================================================================================");
        System.out.println("=== FLUXO DE CONCORRÊNCIA: PASSO A PASSO DA UTILIZAÇÃO DE CORES E ESTADO DA FILA ("
                + algoritmoSelecionado + " | Quantum: " + quantum + ") ===");
        System.out.println(
                "=======================================================================================================================================");

        System.out.printf("%-8s | %-8s | %-12s | %-15s | %-30s | %s\n",
                "Tempo (T)", "Core ID", "Processo", "Ação/Estado", "Detalhes da Ação",
                "Fila de Prontos (Estado ao final do TICK)");
        System.out.println(
                "---------------------------------------------------------------------------------------------------------------------------------------");

        Map<Integer, List<EventLogEntry>> groupedEvents = eventTimeline.stream()
                .collect(Collectors.groupingBy(e -> e.tempo, TreeMap::new, Collectors.toList()));

        for (Map.Entry<Integer, List<EventLogEntry>> entry : groupedEvents.entrySet()) {
            int tempo = entry.getKey();
            List<EventLogEntry> events = entry.getValue();

            // 1. Extrai o estado da Fila
            String queueState = events.stream()
                    .filter(e -> "ESTADO_FILA".equals(e.acao))
                    .map(e -> e.detalhes)
                    .findFirst().orElse("-");

            // 2. Separa eventos de Chegada/Conclusão (coreId=0)
            List<EventLogEntry> nonCoreEvents = events.stream()
                    .filter(e -> e.coreId == 0 && ("CHEGADA".equals(e.acao) || "CONCLUIDO".equals(e.acao)))
                    .collect(Collectors.toList());

            // 3. Separa eventos de Atividade de Core (coreId > 0)
            List<EventLogEntry> coreActivityEvents = events.stream()
                    .filter(e -> e.coreId > 0)
                    .collect(Collectors.toList());

            // 4. Combina todos os eventos a serem exibidos linha por linha
            List<EventLogEntry> displayEvents = new ArrayList<>(nonCoreEvents);
            displayEvents.addAll(coreActivityEvents);

            boolean firstLine = true;
            for (EventLogEntry e : displayEvents) {

                String coreIdStr = e.coreId == 0
                        ? ("CONCLUIDO".equals(e.acao) ? "FIM" : "CHEGADA")
                        : "Core " + e.coreId;
                String processoStr = e.processoNome;

                if (firstLine) {
                    System.out.printf("%-8d | %-8s | %-12s | %-15s | %-30s | %s\n",
                            tempo, coreIdStr, processoStr, e.acao, e.detalhes, queueState);
                    firstLine = false;
                } else {
                    System.out.printf("%-8s | %-8s | %-12s | %-15s | %-30s | %s\n",
                            "", coreIdStr, processoStr, e.acao, e.detalhes, "");
                }
            }

            // 5. Tratamento para ticks onde só houve atualização de Fila e NENHUMA
            // atividade de Core/Chegada
            if (coreActivityEvents.isEmpty() && nonCoreEvents.isEmpty() && !queueState.equals("-")) {
                System.out.printf("%-8d | %-8s | %-12s | %-15s | %-30s | %s\n",
                        tempo, "OCIOSO", "-", "ESPERA_NA_FILA", "Cores ociosos. Houve apenas Fila", queueState);
            }
        }
        System.out.println(
                "=======================================================================================================================================\n");
    }

    // NOVO MÉTODO: TABELA ORIENTADA A PROCESSOS (Gráfico de Gantt)
    public void exibirTabelaGanttProcessos() {
        System.out.println("\n=====================================================================================");
        System.out.println("=== GRÁFICO DE GANTT: ESTADO DE CADA PROCESSO POR TEMPO (T) ===");
        System.out.println("=====================================================================================");

        // 1. Coleta de dados básicos
        Set<String> processNames = todosProcessos.stream().map(p -> p.nome).collect(Collectors.toSet());
        int maxTempo = getTempoAtual();

        // Mapeamento Tempo -> Processo -> Estado
        Map<Integer, Map<String, String>> chartData = new TreeMap<>();

        // 2. Pré-preenche o mapa com estados padrão (Não Chegou / Espera)
        for (int t = 0; t <= maxTempo; t++) {
            chartData.put(t, new HashMap<>());
            for (Processo p : todosProcessos) {
                chartData.get(t).put(p.nome, (t < p.tempoChegada) ? "---" : "ESPERA");
            }
        }

        // 3. Preenche estados de execução/preempção/conclusão a partir do log
        for (EventLogEntry event : eventTimeline) {
            String pName = event.processoNome;
            int t = event.tempo;

            if (!processNames.contains(pName) || t > maxTempo)
                continue;

            Map<String, String> timeEvents = chartData.get(t);
            if (timeEvents == null)
                continue;

            // Lógica de mapeamento de estados, priorizando o evento mais forte no TICK:
            switch (event.acao) {
                case "EXECUÇÃO":
                    // EXECUÇÃO é um estado forte
                    timeEvents.put(pName, "EXE (C" + event.coreId + ")");
                    break;
                case "PREEMPCÃO":
                    // PREEMPCÃO anula EXECUÇÃO ou ESPERA
                    timeEvents.put(pName, "PREEMPT.");
                    break;
                case "CONCLUIDO":
                    // CONCLUIDO é o estado final e mais forte. O processo não pode mais executar.
                    for (int futureT = t; futureT <= maxTempo; futureT++) {
                        if (chartData.get(futureT) != null) {
                            chartData.get(futureT).put(pName, "FIM");
                        }
                    }
                    timeEvents.put(pName, "CONCLUIDO");
                    break;
                case "CHEGADA":
                    // A chegada apenas garante que saia do estado "---" para "ESPERA" (já tratado
                    // no passo 2)
                    break;
            }
        }

        // 4. Geração da Tabela (Visualização)

        // Cabeçalho
        StringBuilder header = new StringBuilder();
        header.append(String.format("%-10s |", "Tempo (T)"));
        for (String name : processNames) {
            header.append(String.format(" %-12s |", name));
        }
        System.out.println(header.toString());

        // Separador
        StringBuilder separator = new StringBuilder();
        separator.append("-----------");
        for (int i = 0; i < processNames.size(); i++) {
            separator.append("-------------");
        }
        System.out.println(separator.toString());

        // Dados
        for (int t = 0; t <= maxTempo; t++) {
            StringBuilder row = new StringBuilder();
            row.append(String.format("%-10d |", t));

            Map<String, String> states = chartData.get(t);
            if (states != null) {
                for (String name : processNames) {
                    String state = states.getOrDefault(name, "N/A");
                    row.append(String.format(" %-12s |", state));
                }
            } else {
                for (int i = 0; i < processNames.size(); i++) {
                    row.append(String.format(" %-12s |", "---"));
                }
            }
            System.out.println(row.toString());
        }
        System.out.println(separator.toString());
    }

    // MÉTODO QUE CALCULA E EXIBE MÉTRICAS (mantido)
    public Map<String, Double> calcularEExibirMetricas(String algoritmo) {
        if (processosConcluidos.isEmpty()) {
            System.out.println("Nenhum processo concluído para calcular métricas.");
            return Collections.emptyMap();
        }

        System.out
                .println("\n=========================================================================================");
        System.out.println(
                "=== TABELA DE RESUMO E MÉTRICAS FINAIS (" + algoritmo + " | Paralelo: " + NUM_CORES + " Cores) ===");
        System.out.println("=========================================================================================");

        double somaTurnaround = 0;
        double somaEspera = 0;

        System.out.printf("%-10s | %-10s | %-10s | %-10s | %-12s | %-10s\n",
                "Processo", "Chegada", "Execução", "Término", "Turnaround", "Espera");
        System.out.println("-----------------------------------------------------------------------------------------");

        for (Processo p : processosConcluidos) {
            int turnaround = p.getTempoTurnaround();
            int espera = p.tempoEspera.get();

            somaTurnaround += turnaround;
            somaEspera += espera;

            System.out.printf("%-10s | %-10d | %-10d | %-10d | %-12d | %-10d\n",
                    p.nome, p.tempoChegada, p.tempoTotalCPU, p.tempoTermino, turnaround, espera);
        }

        int numProcessos = processosConcluidos.size();
        double mediaTurnaround = somaTurnaround / numProcessos;
        double mediaEspera = somaEspera / numProcessos;

        System.out.println("-----------------------------------------------------------------------------------------");
        System.out.printf("Tempo Médio de Turnaround: %.2f\n", mediaTurnaround);
        System.out.printf("Tempo Médio de Espera: %.2f\n", mediaEspera);
        System.out.println("=========================================================================================");

        Map<String, Double> metrics = new HashMap<>();
        metrics.put("TurnaroundMedio", mediaTurnaround);
        metrics.put("EsperaMedia", mediaEspera);
        metrics.put("TempoTotal", (double) getTempoAtual());

        return metrics;
    }
}