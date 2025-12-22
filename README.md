UNIVERSIDADE FEDERAL RURAL DEPERNAMBUCO
DISCIPLINA PARADIGMAS
DOCENTE: SIDNEY NOUGUEIRA
EQUIPE: JOÃO MATHEUS, JOSÉ ALBÉRICO E RODRIGO SILVA DA LUZ
TEMA: SIMULADOR DE ESCALONAMENTO DE PROCESSOS 

README PROJETO VA02

1.Planejamento/detalhes do projeto:
1.1 Título e descrição breve do projeto.
Título: Simulador de Escalonamento Híbrido Multiprocessado: Round Robin e Prioridade Preemptiva.
1.2 Descrição mais detalhada do funcionamento esperado para a implementação.
Simulador de sistema operacional desenvolvido em Java para gerenciar múltiplos núcleos (Cores), suportando os algoritmos Round Robin (RR) para processos de mesma prioridade e Prioridade Preemptiva (PP). O sistema utiliza estruturas de concorrência nativas do Java para garantir a sincronização e a troca de contexto eficiente entre processos com diferentes níveis de importância.
1.3 Ilustração para ajudar a entender o projeto proposto.

<img width="560" height="528" alt="Captura de tela 2025-12-22 194239rdd" src="https://github.com/user-attachments/assets/bf23cadc-c5e9-4638-93c9-8ecbbfed5478" />

2. Entrega
        2.1 informar: 
    • Se o código inicial faz parte de outro projeto existente.
Este código é um projeto original desenvolvido para fins acadêmicos. Logo não foi derivado ou constitui um desenvolvimento de projetos anteriores da disciplina.

    • Instruções para compilar e/ou rodar (caso exista algum detalhe de compilação ou execução)
Requisitos: JDK 11 ou superior.
Compilação: javac SimuladorEscalonamento.java
Execução: java SimuladorEscalonamento
Detalhe: O projeto não exige bibliotecas externas, utilizando apenas o pacote java.util.concurrent e java.util.

    • Quais conceitos/entidades foram implementadas como tarefas (thread ou co-rotina)
Nesta implementação, as seguintes entidades são tratadas como tarefas ou abstrações concorrentes:
    • Cores (CPUs): Implementados via um FixedThreadPool, onde cada thread do pool representa um núcleo de processamento físico tentando consumir processos da fila.
    • Gerador de Processos: Uma thread separada (ou o loop principal) que simula a "Chegada" de processos em tempos reais, inserindo-os na fila enquanto os cores estão executando.

    • Quais recursos (arquivos, servidor, clientes, variáveis, etc) que são compartilhados (por colaboração ou por disputa) entre as tarefas na sua implementação.

Recursos Compartilhados
    • Fila de Prontos (PriorityBlockingQueue): O principal recurso em disputa. Múltiplos Cores tentam retirar processos simultaneamente, enquanto o Gerador de Processos insere novos.
    • Relógio Global (Tick): Variável compartilhada por colaboração para sincronizar o tempo lógico da simulação entre todas as entidades.
    • Lista de Cores: Para monitorar qual processo está em qual CPU e permitir a preempção por prioridade.

    • Quais mecanismos de sincronização foram usados para o controle da concorrência (usando semáforo, mutex, wait/notify, pool, alguma coisa não vista na disciplina).




Mecanismos de Sincronização
    • PriorityBlockingQueue: Utilizada para garantir que o acesso à fila de processos seja thread-safe sem a necessidade de locks manuais complexos.
    • AtomicInteger: Para o contador de tempo e IDs de processos, garantindo operações atômicas sem risco de condições de corrida.
    • ReentrantLock / Synchronized: Utilizados no método de Preempção, garantindo que apenas um processo seja retirado/trocado de um core por vez.
    • Wait/Notify (ou Thread.sleep): Para simular a passagem do tempo de execução de cada instrução de CPU.

    • Se usou algo não visto na disciplina, indicar.
Utilizamos os conceitos demonstrados e indicados na disciplina

    • Se a implementação é parametrizada (por constantes globais ou por um menu do usuário). Exemplo: o sistema permite alterar o número de clientes, número de arquivos, quantidade de threads, etc.

O sistema é parametrizado por constantes globais localizadas no cabeçalho da classe principal, permitindo alterar facilmente:
    • NUM_CORES (ex: 3, 4, 8);
    • QUANTUM_TIME (ex: 3s);
    • ALGORITMO_ATIVO (RR, PP ou Híbrido);
    • TEMPO_DE_CONTEXTO (custo em ticks para trocar um processo).
    • O sistema permite que o usuário crie “ n ”processos 

    • Como a IA generativa foi utilizada (ferramentas, modelos, partes onde o uso de IA foi mais intenso).
Ferramentas: Gemini (Google) e ChatGPT (OpenAI).
Intensidade de Uso:
    • Alta: Na estruturação lógica da tabela de fluxo e correção de inconsistências de tempo (como o caso do processo Download no Core 1/Core 2).
    • Média: como suporte na geração dos códigos da classe SimuladorEscalonamento.java( estruturas avançadas de concorrencia e comparadores de prioridade).
    • Baixa: Na definição dos conceitos teóricos de SO, que serviram apenas como validação de conhecimento prévio.


