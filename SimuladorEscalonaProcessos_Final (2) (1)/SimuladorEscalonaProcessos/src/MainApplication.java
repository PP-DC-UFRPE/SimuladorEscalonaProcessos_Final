
/**
 * CLASSE: MainApplication
 * * FUNÇÃO: Interface do Usuário e Orquestração de Testes.
 * * DESCRIÇÃO: 
 * Responsável por interagir com o usuário: coleta os dados iniciais dos processos, 
 * seleciona o algoritmo (RR ou PP) e o Quantum. Orquestra a execução das simulações 
 * e chama os métodos de exibição de métricas, log de Cores e, finalmente, 
 * realiza a comparação de desempenho entre os dois algoritmos.
 * * CONCORRÊNCIA: 
 * Esta classe opera sequencialmente (em uma única thread) e apenas configura e 
 * inicia a execução paralela e concorrente, aguardando a conclusão da simulação 
 * antes de exibir os resultados finais.
 */
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class MainApplication {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        SimuladorEscalonamento simulador = new SimuladorEscalonamento();

        List<Processo> processosIniciais = criarProcessos(scanner);

        simulador.setProcessos(processosIniciais);

        Object[] escolha = selecionarAlgoritmoEQuantum(scanner);
        String alg1 = (String) escolha[0];
        int quantum1 = (int) escolha[1];

        Map<String, Double> metrics1 = null;
        Map<String, Double> metrics2 = null;
        String alg2 = "";

        // 3. Executa a primeira simulação
        simulador.executarSimulacao(alg1, quantum1);
        metrics1 = simulador.calcularEExibirMetricas(alg1);
        simulador.exibirTabelaTimeline();

        // Chamada para a nova tabela de Gantt
        simulador.exibirTabelaGanttProcessos();

        // --- Orquestração da Segunda Simulação ---
        alg2 = alg1.equals("RR") ? "PP" : "RR";
        String nomeAlg2 = alg2.equals("RR") ? "Round Robin" : "Prioridade Preemptiva";
        int quantum2 = alg2.equals("RR") ? 3 : 1;

        System.out.println("\n----------------------------------------------------------------");
        System.out.print("Deseja rodar o algoritmo " + nomeAlg2 + " (o outro) com os mesmos processos? (s/n): ");
        String resposta = scanner.next().toLowerCase();

        if (resposta.equals("s")) {
            if (alg2.equals("RR")) {
                System.out.print("Defina o Quantum para " + nomeAlg2 + " (entre 1 e 4 ciclos): ");
                while (!scanner.hasNextInt()) {
                    System.out.print("Valor inválido. Insira um número inteiro para o Quantum: ");
                    scanner.next();
                }
                quantum2 = scanner.nextInt();
                while (quantum2 < 1 || quantum2 > 4) {
                    System.out.println("⚠️ Valor inválido. O Quantum deve ser entre 1 e 4.");
                    System.out.print("Defina o Quantum para " + nomeAlg2 + " (entre 1 e 4 ciclos): ");
                    quantum2 = scanner.nextInt();
                }
            }

            // 4. Executa a segunda simulação
            simulador.executarSimulacao(alg2, quantum2);
            metrics2 = simulador.calcularEExibirMetricas(alg2);
            simulador.exibirTabelaTimeline();
            simulador.exibirTabelaGanttProcessos(); // Chamada para a nova tabela de Gantt

            // 5. Comparação Final
            compararPerformance(alg1, metrics1, alg2, metrics2);

        } else {
            System.out.println("Simulação finalizada.");
        }

        scanner.close();
    }

    private static void compararPerformance(String alg1, Map<String, Double> m1, String alg2, Map<String, Double> m2) {
        if (m1 == null || m2 == null || m1.isEmpty() || m2.isEmpty()) {
            System.out.println("\nNão foi possível realizar a comparação de performance devido à falta de métricas.");
            return;
        }

        System.out.println("\n\n==================================================");
        System.out.println("=== COMPARAÇÃO DE PERFORMANCE ENTRE " + alg1 + " E " + alg2 + " ===");
        System.out.println("==================================================");

        double ta1 = m1.get("TurnaroundMedio");
        double ta2 = m2.get("TurnaroundMedio");
        double es1 = m1.get("EsperaMedia");
        double es2 = m2.get("EsperaMedia");
        double tt1 = m1.get("TempoTotal");
        double tt2 = m2.get("TempoTotal");

        System.out.println("\n--- Resumo de Métricas ---");
        System.out.printf("%-15s | %-15s | %-15s\n", "Métrica", alg1, alg2);
        System.out.println("--------------------------------------------------");
        System.out.printf("%-15s | %-15.2f | %-15.2f\n", "Turnaround Médio", ta1, ta2);
        System.out.printf("%-15s | %-15.2f | %-15.2f\n", "Espera Média", es1, es2);
        System.out.printf("%-15s | %-15.0f | %-15.0f\n", "Tempo Total (T)", tt1, tt2);
        System.out.println("--------------------------------------------------");

        System.out.println("\n--- Análise Resumida ---");

        // Comparação de Turnaround (Melhor é o menor)
        String melhorTA = (ta1 < ta2) ? alg1 : alg2;
        String analiseTA = String.format(
                "Em termos de **tempo de conclusão total (Turnaround Médio)**, %s foi %.2f%% mais eficiente que %s.",
                melhorTA,
                Math.abs(ta1 - ta2) / Math.min(ta1, ta2) * 100,
                (melhorTA.equals(alg1) ? alg2 : alg1));
        System.out.println("- " + analiseTA);

        // Comparação de Espera (Melhor é o menor)
        String melhorES = (es1 < es2) ? alg1 : alg2;
        String analiseES = String.format(
                "Em termos de **tempo de espera na fila (Espera Média)**, %s demonstrou menor inanição e atraso, sendo %.2f%% melhor que %s.",
                melhorES,
                Math.abs(es1 - es2) / Math.min(es1, es2) * 100,
                (melhorES.equals(alg1) ? alg2 : alg1));
        System.out.println("- " + analiseES);

        // Conclusão Geral
        if (ta1 + es1 < ta2 + es2) {
            System.out.println("\nCONCLUSÃO: O algoritmo **" + alg1
                    + "** apresentou a melhor performance geral neste conjunto de processos.");
        } else {
            System.out.println("\nCONCLUSÃO: O algoritmo **" + alg2
                    + "** apresentou a melhor performance geral neste conjunto de processos.");
        }
        System.out.println("==================================================");
    }

    public static List<Processo> criarProcessos(Scanner scanner) {
        List<Processo> processos = new ArrayList<>();
        System.out.println("\n--- Criação de Processos ---");
        boolean adicionarMais = true;
        int idCounter = 1;

        while (adicionarMais) {
            System.out.println("\nCriando Processo P" + idCounter + ":");

            System.out.print("Nome: ");
            String nome = scanner.next();

            System.out.print("Prioridade (ex: 1 para alta, 5 para baixa): ");
            while (!scanner.hasNextInt()) {
                System.out.print("Valor inválido. Insira um número inteiro para a prioridade: ");
                scanner.next();
            }
            int prioridade = scanner.nextInt();

            System.out.print("Tipo (I/O bound ou CPU bound): ");
            String tipo = scanner.next();

            System.out.print("Tempo Total de CPU (ciclos): ");
            while (!scanner.hasNextInt()) {
                System.out.print("Valor inválido. Insira um número inteiro para o Tempo Total de CPU: ");
                scanner.next();
            }
            int tempoTotalCPU = scanner.nextInt();

            System.out.print("Tempo de Chegada (ciclo de tempo): ");
            while (!scanner.hasNextInt()) {
                System.out.print("Valor inválido. Insira um número inteiro para o Tempo de Chegada: ");
                scanner.next();
            }
            int tempoChegada = scanner.nextInt();

            Processo novoProcesso = new Processo(idCounter++, nome, prioridade, tipo, tempoTotalCPU, tempoChegada);
            processos.add(novoProcesso);
            System.out.println("Processo " + novoProcesso.nome + " criado.");

            System.out.print("Adicionar mais processos? (s/n): ");
            String resposta = scanner.next().toLowerCase();
            if (resposta.equals("n") || resposta.equals("nao") || resposta.equals("não")) {
                adicionarMais = false;
            }
        }
        return processos;
    }

    private static Object[] selecionarAlgoritmoEQuantum(Scanner scanner) {
        System.out.println("\n--- Seleção de Algoritmo ---");
        System.out.println("1. Round Robin (RR)");
        System.out.println("2. Prioridade Preemptiva (PP)");
        System.out.print("Escolha o algoritmo (1 ou 2): ");

        while (!scanner.hasNextInt()) {
            System.out.print("Escolha inválida. Digite 1 ou 2: ");
            scanner.next();
        }
        int escolha = scanner.nextInt();

        if (escolha == 1) {
            int quantum = 1;
            do {
                System.out.print("Defina o Quantum de tempo para Round Robin (entre 1 e 4 ciclos): ");
                while (!scanner.hasNextInt()) {
                    System.out.print("Valor inválido. Insira um número inteiro para o Quantum: ");
                    scanner.next();
                }
                quantum = scanner.nextInt();
                if (quantum < 1 || quantum > 4) {
                    System.out.println("⚠️ Valor inválido. O Quantum deve ser entre 1 e 4.");
                }
            } while (quantum < 1 || quantum > 4);
            return new Object[] { "RR", quantum };
        } else {
            System.out.println("Prioridade Preemptiva selecionada. Quantum = 1 (fixo).");
            return new Object[] { "PP", 1 };
        }
    }
}