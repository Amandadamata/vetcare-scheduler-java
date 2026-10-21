# VetCare Scheduler

[![Java CI](https://github.com/Amandadamata/vetcare-scheduler-java/actions/workflows/ci.yml/badge.svg)](https://github.com/Amandadamata/vetcare-scheduler-java/actions/workflows/ci.yml)

[English](README.md) · **Português (Brasil)**

O VetCare Scheduler é uma aplicação Java de linha de comando para consultas veterinárias. Ela mantém
uma agenda separada para cada veterinário ou sala, calcula uma prioridade de triagem simplificada,
impede sobreposição de consultas na mesma agenda, acompanha cada consulta até ela ser concluída,
cancelada ou marcada como falta, e permite que a clínica exclua ou anonimize dados pessoais.

É um projeto pessoal de aprendizado, criado para transformar fundamentos de Java e de
desenvolvimento de software em uma aplicação funcional com regras de negócio, persistência em
arquivo, migração de dados e testes automatizados.

## Funcionalidades

- Cadastrar e atualizar tutores e animais; buscar animais por parte do nome
- Agendas (recursos): uma por veterinário, sala ou outro recurso que atende uma consulta por vez
- Agendar, atualizar, remarcar (inclusive para outra agenda), listar, filtrar e cancelar consultas
- Detecção de conflitos por agenda: duas agendas podem marcar o mesmo horário; a mesma agenda, não
- Ciclo de vida da consulta: agendada → concluída, falta ou cancelada, com transições validadas
- Prioridade de triagem `EMERGENCY`, `URGENT` ou `STANDARD` calculada pela condição e pelos sinais clínicos
- Fila de triagem do dia (`PriorityQueue`) e resumo diário por status, prioridade e tipo
- Excluir um tutor (com animais e consultas) ou um animal; ou anonimizar um tutor mantendo o histórico de consultas
- Armazenamento local em JSON com gravação atômica, validação ao carregar e migração automática de arquivos antigos, com backup

## Lógica de negócio

### Agendas e conflitos

Cada consulta pertence a exatamente uma **agenda** (`ScheduleResource`): "Dra. Ana", "Sala 2" e
assim por diante. Duas consultas entram em conflito quando estão na **mesma agenda** e seus
intervalos de tempo se sobrepõem:

```java
candidateStart.isBefore(existingEnd) && existingStart.isBefore(candidateEnd)
```

- A Dra. Ana às 14h não bloqueia o Dr. Bruno às 14h.
- Consultas encostadas (10:00–10:30 e 10:30–11:00) são permitidas.
- Só contam as consultas que ainda ocupam o horário: **agendadas** e **concluídas**. Consultas
  **canceladas** e **faltas** liberam o horário.

Um único tipo genérico de recurso cobre veterinários e salas. Modelar os dois ao mesmo tempo (um
veterinário *e* uma sala por consulta) exigiria verificar dois tipos de conflito, o que os
requisitos atuais não pedem.

### Ciclo de vida da consulta

```text
              ┌──> COMPLETED   (atendida; continua ocupando o horário)
SCHEDULED ────┼──> NO_SHOW     (paciente faltou; libera o horário)
              └──> CANCELLED   (libera o horário)
```

- Só consultas agendadas podem ser atualizadas, remarcadas ou mudar de status. Os outros três
  status são finais.
- Concluída e falta só podem ser registradas **depois do início da consulta**. Nada é marcado como
  concluído automaticamente: a passagem do tempo não comprova que o atendimento aconteceu.

### Prioridade de triagem

1. `EMERGENCY`: condição crítica, dificuldade respiratória, sangramento descontrolado, inconsciência ou convulsão.
2. `URGENT`: condição preocupante, dor intensa, vômitos repetidos ou incapacidade de ficar em pé.
3. `STANDARD`: nenhuma regra de emergência ou urgência se aplica.

A fila do dia ordena as consultas agendadas por prioridade, depois pela hora de registro (ordem de
chegada) e depois pelo horário.

> Essas regras foram simplificadas de propósito para um projeto de aprendizado de software. Elas não
> são um protocolo veterinário nem orientação clínica.

### Exclusão e anonimização de dados pessoais

| Operação | O que acontece | O que é mantido |
|---|---|---|
| Excluir tutor | O tutor, seus animais e todas as consultas desses animais são removidos | Nada |
| Excluir animal | O animal e suas consultas são removidos | O tutor e os outros animais |
| Anonimizar tutor | Nome, telefone e e-mail do tutor são substituídos; nomes dos animais e todas as observações são apagados | Datas, tipos, condição, sinais clínicos, prioridades e status das consultas; espécie e data de nascimento dos animais |

- As três operações são recusadas enquanto alguma consulta relacionada estiver **agendada**: cancele
  ou registre o resultado antes.
- Os registros são removidos ou substituídos por inteiro, então nenhum animal aponta para um tutor
  inexistente e nenhuma consulta aponta para um animal inexistente.
- A CLI mostra o que será afetado e exige digitar `DELETE` ou `ANONYMIZE`.
- Se a gravação falhar, a alteração é desfeita na memória.

Essas operações ajudam a clínica a atender um pedido de exclusão. Elas **não** tornam, por si só, a
aplicação conforme a LGPD ou qualquer outra lei: não há política de retenção nem controle de acesso,
e backups antigos (veja abaixo) ainda contêm os dados originais.

### Persistência e arquivos antigos

Os dados ficam em `data/vetcare-data.json`, gravados com Gson. A aplicação grava um arquivo
temporário e depois substitui o principal, então uma gravação interrompida não corrompe o arquivo.
Na inicialização, cada registro é validado: IDs únicos, tutores, animais e agendas existentes,
valores de enum conhecidos e as mesmas regras de campo usadas nas novas entradas.

Os arquivos agora têm `"schemaVersion": 2`. Ao encontrar um arquivo da versão anterior (sem
agendas), a aplicação:

1. converte em memória, colocando as consultas existentes em uma agenda chamada `Main schedule`;
2. valida o resultado; se for inválido, nada é gravado;
3. copia o arquivo original para `vetcare-data.json.v1-backup.json` (nunca sobrescreve um backup existente);
4. grava os dados convertidos e informa onde está o backup.

Arquivos gravados por uma versão mais nova são recusados sem alterações. Os dados JSON locais e os
backups são ignorados pelo Git.

## Conceitos de programação demonstrados

- Java 21, orientação a objetos, encapsulamento e classes pequenas e focadas
- Arquitetura em camadas (`cli`, `service`, `repository`, `persistence`, `model`) e injeção por construtor
- Programação para interfaces (`ClinicDataStore`)
- Coleções: `Map`, `Set`, `EnumSet`, `EnumMap` e `PriorityQueue`
- Comparators, streams, lambdas e `Optional`
- Lógica de intervalos de data e hora com `java.time`
- Máquina de estados expressa com um enum
- Integridade referencial, migração de dados com backup e validação dos dados carregados
- Testes automatizados com JUnit 6; Maven e GitHub Actions

## Estrutura do projeto

```text
src/main/java/com/amandadamata/vetcare/
├── cli/          # Menu do console e leitura de entradas
├── exception/    # Erros de domínio e de persistência
├── model/        # Owner, Animal, Appointment, ScheduleResource, enums
├── persistence/  # Leitura/gravação do JSON, validação, migração
├── repository/   # Coleções em memória
├── service/      # Agendamento, triagem, conflitos, relatórios, agendas, privacidade
├── util/         # Validações compartilhadas
└── Main.java     # Montagem e inicialização da aplicação

src/test/java/        # Testes de unidade, integração, migração e CLI
src/test/resources/   # Um arquivo de dados gravado pela versão anterior, para os testes de migração
docs/                 # Arquitetura, decisões, testes e walkthrough
```

## Requisitos

- Java 21 ou superior
- Acesso à internet no primeiro build, para o Maven Wrapper baixar o Maven e as dependências

## Como executar

```bash
git clone https://github.com/Amandadamata/vetcare-scheduler-java.git
cd vetcare-scheduler-java
./mvnw clean package
java -jar target/vetcare-scheduler.jar
```

No Windows, use `mvnw.cmd` no lugar de `./mvnw`. Para guardar dados de demonstração em outro lugar:

```bash
VETCARE_DATA_FILE=/tmp/vetcare-demo.json java -jar target/vetcare-scheduler.jar
```

Antes de agendar, cadastre pelo menos uma agenda (opção 17 do menu). Com uma única agenda, ela é
escolhida automaticamente.

A interface da aplicação está em inglês.

## Testes

```bash
./mvnw test
```

A suíte cobre regras de triagem, fronteiras de intervalos, conflitos por agenda, agendamentos
simultâneos em agendas diferentes, todas as transições de status permitidas e proibidas, registro de
comparecimento antes do início, exclusão e anonimização sem referências quebradas, restauração da
memória após falha ao salvar, arquivos de dados inválidos, migração de um arquivo gravado pela versão
anterior e sessões completas do menu. Veja [docs/TESTING.md](docs/TESTING.md).

O GitHub Actions executa a verificação completa do Maven com Java 21 e 25 a cada push e pull request.

## Limitações atuais

- Cada consulta usa um recurso; reservar um veterinário **e** uma sala ao mesmo tempo não é modelado.
- Não há prazos de retenção, controle de acesso nem criptografia; os backups de migração guardam os dados originais até serem apagados manualmente.
- A anonimização mantém espécie e data de nascimento dos animais, para estatísticas.
- Um processo por vez; JSON não oferece acesso concorrente nem transações de banco de dados.
- Se uma atualização (não uma exclusão ou anonimização) falhar ao salvar, a alteração fica em memória até a próxima gravação bem-sucedida; a CLI avisa o usuário.
- Não há autenticação, API de rede nem interface gráfica.
- As regras de triagem são exemplos educacionais e não devem ser usadas em decisões clínicas.

## O que aprendi

Este projeto me levou de exercícios isolados de Java para o fluxo completo de uma aplicação: decidir
onde cada regra deve ficar, representar intervalos de tempo corretamente, usar coleções com um
propósito real, modelar um ciclo de vida com transições explícitas, manter as referências válidas
quando dados são removidos e evoluir o formato de um arquivo gravado sem perder os dados existentes.

## Documentação (em inglês)

- [Arquitetura](docs/ARCHITECTURE.md): diagramas de camadas, domínio e sequência; máquina de estados; exclusão; migração
- [Decisões de projeto](docs/DESIGN_DECISIONS.md): o que foi escolhido, por quê, alternativas e os testes que verificam
- [Testes](docs/TESTING.md): como executar e o que cada classe verifica
- [Walkthrough do projeto](docs/PROJECT_WALKTHROUGH.md): explicação classe por classe e perguntas de entrevista

## Licença

Este projeto está disponível sob a [Licença MIT](LICENSE).
