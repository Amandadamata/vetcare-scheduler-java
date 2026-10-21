# VetCare Scheduler

[![Java CI](https://github.com/Amandadamata/vetcare-scheduler-java/actions/workflows/ci.yml/badge.svg)](https://github.com/Amandadamata/vetcare-scheduler-java/actions/workflows/ci.yml)

[English](README.md) · **Português (Brasil)**

O VetCare Scheduler é uma aplicação Java de linha de comando para gerenciar consultas veterinárias, calcular uma prioridade de triagem simplificada e impedir conflitos de horário.

É um projeto pessoal de aprendizado, criado para transformar fundamentos de Java e de desenvolvimento de software em uma aplicação funcional com regras de negócio, persistência em arquivo e testes automatizados.

## Funcionalidades

- Cadastrar e atualizar tutores e animais
- Buscar animais por parte do nome
- Agendar, atualizar, remarcar, listar, filtrar e cancelar consultas (até dois anos à frente)
- Detectar sobreposição de consultas em uma agenda única da clínica
- Calcular a prioridade de triagem `EMERGENCY`, `URGENT` ou `STANDARD`
- Ordenar a fila de triagem do dia usando `PriorityQueue`
- Gerar um resumo diário por status, prioridade e tipo de consulta
- Salvar os dados localmente em JSON e carregá-los na próxima execução, recusando arquivos danificados ou editados com dados inválidos
- Validar as entradas do usuário e exibir erros de domínio claros

A interface da aplicação está em inglês.

## Lógica de negócio

### Prioridade de triagem

A aplicação calcula a prioridade em vez de pedir que o usuário a escolha:

1. `EMERGENCY`: condição crítica, dificuldade respiratória, sangramento descontrolado, inconsciência ou convulsão.
2. `URGENT`: condição preocupante, dor intensa, vômitos repetidos ou incapacidade de ficar em pé, quando nenhuma regra de emergência se aplica.
3. `STANDARD`: nenhuma regra de emergência ou urgência se aplica.

A fila do dia ordena primeiro pela prioridade. Dentro da mesma prioridade, vem antes a consulta registrada primeiro (como em uma fila de triagem, em que a ordem de chegada desempata), depois o horário agendado e, por fim, o ID.

A fila é montada com uma `PriorityQueue` e esvaziada com `poll()`, que sempre devolve a consulta de maior prioridade. Gerar a lista ordenada completa dessa forma custa O(n log n), o mesmo que ordenar uma lista; a `PriorityQueue` traria ganho se a fila fosse mantida entre operações e as consultas fossem retiradas uma de cada vez. Ela é usada aqui porque representa diretamente a pergunta "quem é o próximo?" e porque percorrer uma `PriorityQueue` sem `poll()` **não** devolve os elementos em ordem.

> Essas regras foram simplificadas de propósito para um projeto de aprendizado de software. Elas não são um protocolo veterinário nem orientação clínica.

### Detecção de conflitos de horário

A versão atual representa uma única sala/equipe da clínica. Duas consultas ativas entram em conflito quando seus intervalos de tempo se sobrepõem:

```java
candidateStart.isBefore(existingEnd)
        && existingStart.isBefore(candidateEnd)
```

Consultas encostadas são permitidas: `10:00–10:30` e `10:30–11:00` não se sobrepõem. Consultas canceladas deixam de bloquear o horário, e uma consulta ignora a si mesma quando é remarcada.

### Persistência

Os dados ficam em `data/vetcare-data.json`, gravados com Gson. As datas usam o padrão ISO-8601, que é legível e não ambíguo. A aplicação grava primeiro um arquivo temporário e só então substitui o arquivo principal, reduzindo a chance de deixar um arquivo gravado pela metade.

O Gson cria os objetos sem passar pelos serviços, então os dados carregados escapariam de todas as regras de negócio. Na inicialização, o `ClinicSnapshotValidator` verifica cada registro: IDs únicos, referências a tutores e animais existentes, valores de enum conhecidos e as mesmas regras de campo usadas para novas entradas. Se algo estiver errado, a aplicação informa o ID do registro e o problema, não inicia e mantém o arquivo intacto para inspeção.

Os dados JSON locais são ignorados pelo Git, para que registros pessoais de teste não sejam publicados.

## Conceitos de programação demonstrados

- Java 21 e programação orientada a objetos
- Encapsulamento e classes pequenas e focadas
- Coleções: `Map`, `Set`, `List` e `PriorityQueue`
- Comparators, streams, ordenação e filtragem
- Lógica de intervalos de data e hora
- Validação de entradas e exceções personalizadas
- Leitura e gravação de JSON com fronteiras de persistência
- Injeção de dependências por construtor
- Testes automatizados com JUnit 5
- Build com Maven e integração contínua com GitHub Actions

## Estrutura do projeto

```text
src/main/java/com/amandadamata/vetcare/
├── cli/          # Menu do console e leitura de entradas
├── exception/    # Erros de domínio e de persistência
├── model/        # Tutor, animal, consulta e enums
├── persistence/  # Leitura, validação e gravação do JSON
├── repository/   # Acesso às coleções em memória
├── service/      # Validação, triagem, agendamento e relatórios
├── util/         # Validações compartilhadas
└── Main.java     # Montagem e inicialização da aplicação

src/test/java/    # Testes unitários e de integração
docs/             # Arquitetura, decisões, testes e walkthrough
```

A `Main` apenas conecta repositório, persistência, serviços e CLI. As regras de negócio ficam fora da interface de console para que possam ser testadas sem digitar no terminal.

## Requisitos

- Java 21 ou superior
- Acesso à internet no primeiro build, para o Maven Wrapper baixar o Maven e as dependências

Não é necessário ter o Maven instalado.

## Como executar

Clone o repositório e entre na pasta:

```bash
git clone https://github.com/Amandadamata/vetcare-scheduler-java.git
cd vetcare-scheduler-java
```

Execute os testes:

```bash
./mvnw clean test
```

Gere o JAR executável:

```bash
./mvnw clean package
```

Inicie a aplicação:

```bash
java -jar target/vetcare-scheduler.jar
```

No Windows, use `mvnw.cmd` no lugar de `./mvnw`.

Para guardar os dados de teste em outro lugar, defina `VETCARE_DATA_FILE` antes de iniciar:

```bash
VETCARE_DATA_FILE=/tmp/vetcare-demo.json java -jar target/vetcare-scheduler.jar
```

## Testes

A suíte de testes cobre:

- decisões de triagem de emergência, urgência e padrão
- precedência das regras de emergência sobre as de urgência
- intervalos parcialmente sobrepostos, contidos e encostados
- consultas canceladas ignoradas e remarcação segura
- validação de campos obrigatórios, e-mail, telefone, datas e duração
- ordenação cronológica, filtros combinados e ordem de triagem
- cancelamento liberando o horário
- totais e agrupamentos do relatório diário
- persistência JSON de ida e volta e tratamento de arquivo inválido
- recusa de JSON válido com dados impossíveis (enums desconhecidos, durações fora da faixa, referências quebradas, IDs duplicados)
- cadastro, atualização e busca de tutores e animais, e o caminho de falha ao salvar
- atualizações clínicas que recalculam a prioridade de triagem
- sessões completas do menu com entrada de teclado simulada

O GitHub Actions executa a verificação completa do Maven a cada push e pull request. Veja [docs/TESTING.md](docs/TESTING.md) para saber o que cada classe de teste verifica.

## Limitações atuais

- A agenda representa uma sala/equipe, e não vários veterinários ou recursos.
- A aplicação é local e pensada para um processo em execução por vez.
- JSON é adequado ao escopo de aprendizado, mas não oferece concorrência de banco de dados nem migrações de esquema.
- Não há autenticação, API de rede nem interface gráfica.
- Tutores e animais não podem ser excluídos; dados pessoais só podem ser removidos editando ou apagando o arquivo de dados.
- Consultas passadas continuam como `SCHEDULED`; não existe status de concluída ou falta.
- Se a gravação falhar, a alteração fica em memória até a próxima gravação bem-sucedida. A CLI avisa o usuário quando isso acontece.
- As regras de triagem são exemplos educacionais e não devem ser usadas em decisões clínicas.

Esses limites são intencionais: o projeto foca em lógica Java compreensível, separação de responsabilidades, testes e documentação.

## O que aprendi

Este projeto me ajudou a passar de exercícios isolados de Java para o fluxo completo de uma aplicação. As lições mais importantes foram decidir onde cada regra deve ficar, representar intervalos de tempo corretamente, usar coleções com um propósito real, separar operações de arquivo da lógica de negócio e testar comportamentos em vez de apenas métodos isolados.

## Documentação (em inglês)

- [Arquitetura](docs/ARCHITECTURE.md): diagramas de camadas e de sequência, inicialização, tratamento de erros, gravação
- [Decisões de projeto](docs/DESIGN_DECISIONS.md): o que foi escolhido, por quê, alternativas e os testes que verificam
- [Testes](docs/TESTING.md): como executar e o que cada classe verifica
- [Walkthrough do projeto](docs/PROJECT_WALKTHROUGH.md): explicação classe por classe e perguntas de entrevista

## Licença

Este projeto está disponível sob a [Licença MIT](LICENSE).
