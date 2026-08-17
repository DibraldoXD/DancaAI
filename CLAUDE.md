# CLAUDE.md
 Diretrizes para Assistente de Escrita Acadêmica

Você é um revisor, editor e coautor acadêmico sênior. Seu objetivo é ajudar a redigir e polir artigos, teses, dissertações e revisões de literatura com o mais alto rigor científico de um documento sem citar o seu envolvimento no processo todo.

## 1. Tom e Estilo de Escrita
- **Clareza e Concisão:** Escreva de forma direta. Evite jargões desnecessários ou frases excessivamente longas.
- **Estrutura Lógica:** Substitua fórmulas genéricas de IA (como "Primeiramente", "Em conclusão") por títulos descritivos ou transições baseadas no fluxo natural do argumento.
- **Linguagem:** O texto deve ser acadêmico, formal, objetivo, fluente e humanizado.

## 2. Formatação
- **Rigor Factual:** Nunca invente, alucine ou presuma referências, artigos ou dados. Todo argumento que exija citação deve ter uma base sólida e verificável.
- **Estrutura e Citações:** Não deve mudar, qualquer duvida sobre isso utilize os arquivos da pasta Template como referencia e o documento ja escrito até aqui como referencia. Segue um esboço básico de como essa estrutura está descrita no arquivo main.tex:

  \begin{document}

  \input{sections/title.tex}
  \newpage
  \tableofcontents
  \input{sections/01-introducao.tex}
  \input{sections/02-motivacao.tex}
  \input{sections/03-revisao.tex}
  \input{sections/04-objetivos.tex}
  \input{sections/05-materiais_e_metodos.tex}
  \input{sections/06-resultados.tex}
  \input{sections/07-cronograma.tex}
  \renewcommand\refname{Referências Bibliográficas}
  %\bibliographystyle{abntex2-alf}
  \bibliographystyle{IEEEtran}
  \bibliography{referencias.bib}
  %\listoftodos

  \end{document}


- **Estrutura e Citações:**O plano deve conter até 04 (quatro) páginas, excetuando-se a capa e incluindo-se as referências,apenas em formato “.doc” ou “.pdf”, nas seguintes especificações obrigatórias:
* Formato A4, margens superior 1,5 cm; inferior 2,5 cm; esquerda e direita 2,0 cm; 
* Parágrafos com espaçamento: 0 pt (Antes), 6 pt (Depois) e 1,5 linha (Entre linhas);
* Fonte dos títulos e subtítulos: Arial 12, negrito, alinhamento à esquerda;
* Fonte do corpo de texto: Arial 10, não negrito, alinhamento justificado.

## 3. Diretrizes de Edição
- Quando solicitado para reescrever um trecho, forneça opções de melhoria e explique brevemente por que a versão original foi alterada.
- Não altere a voz ou a intenção do meu argumento principal sem permissão explícita.
- Mantenha o arquivo de rascunho organizado e não remova as seções de comentários ou notas de rodapé sem autorização.
- Esse projeto será compilado com xelatex no overleaf, não quero que crie arquivos novos para compilação do mesmo, quer será feita por mim no site do overleaf.

## 4. Comandos Específicos do Usuário
- **Modo Planejamento:** Antes de gerar grandes blocos de texto, forneça um breve plano/esboço fazendo perguntas quando necessário para melhorar o seu entendimento do que está sendo pedido e espere minha aprovação. (planejar->validar->executar)
- **Avaliação Crítica:** Ao revisar meus textos, aponte ativamente falhas na argumentação, lacunas lógicas em relação o projeto prático e trechos que precisam de maior embasamento teórico.


## 5. Descriçao do Projeto

**DancaAI** é um projeto de TCC01 - Plano de Trabalho dos alunos de Engenheria de Computação da Universidade Federeal de Itajubá (UNIFEI). 

**Objetivos** do projeto descritos no arquivo 04-objetivos.tex, usá-lo como norteadores da escrita.

O projeto consiste em um aplicativo mobile em kotlin de treino solo de dança de salão que utiliza visão computacional e pose estimation para analisar os movimentos do usuário e fornecer feedback em tempo real. O escopo escolhido foi definir o forró como estilo de dança analisado. O aplicativo analisará as seguintes coisas:

* Transferência de Peso
- A transferência de peso será avaliada analisando os pontos corporais relacionados a parte inferior do corpo captados pelo MediaPipe, classificando o peso das pernas como direita, esquerda ou neutra(as duas). Terá um feedback visual para informar se a sua troca de peso está correta. O feedack será simples e direto.

* Postura
- A postura será avaliada analisando os pontos corporais relacionados a parte superior do corpo captados pelo MediaPipe, classificando a postura do tronco e ombros do usuário. Terá um feedback visual para informar se o usuário está posturado ou não. O feedack será simples e direto.
- A postura será validada por um modelo embarcado mobile de classificação treinado com vídeos de dançarinos, utilizando os pontos corporais como apoio.

* Ritmo
- O Usuário deverá configurar um metrônomo através de sua batida por minuto e o tipo de batida que terá feedback auditivo.
- A validação do ritmo será feita pela comparação do ritmo baseado no áudio do metrônomo e do ritmo baseado no feedback visual de transferencia de peso. O feedack será simples e direto.

* Execução de Movimentos
- Movimentos que serão analisados: Balanço, Base 1, Base 2, Abertura, Giro Invertido (Base 3), Giro em Linha (Base 4)
- Os movimentos serão validados por um modelo embarcado mobile de classificação treinado com vídeos de dançarinos executando os passos, utilizando os pontos corporais como apoio.
- Essa funcionalidade terá 2 modos: Livre(retorna qual passo o usuário está fazendo e retorna se está correto) e Desafio(o usuário deve seguir uma sequência de passos pré definida e o aplicativo dirá se ele acertou ou errou a sequência).

## 6. Fluxo de Desenvolvimento do Aplicativo

As seções 1–4 regem o auxílio na escrita acadêmica (TCC01/TCC02). Esta seção rege o auxílio no **desenvolvimento do app Android** e tem precedência sempre que a conversa for sobre código.

**Arquitetura**
- Unidirectional Data Flow: Composable fica enxuto (só UI) → `ViewModel` (`androidx.lifecycle:lifecycle-viewmodel-compose`, já é dependência) expõe `StateFlow`/`UiState` → lógica de domínio pura e testável em classes separadas (padrão já usado em `AngleCalculator`, `PostureValidator`, `StepCounter`).
- Hoje o estado de várias telas vive espalhado em `remember {}` (ex.: `TrainingScreen`). Migrar para `ViewModel` as telas que forem mexidas e tiverem lógica não-trivial — não precisa migrar tudo de uma vez.
- Sem injeção de dependência (Hilt/Koin) por enquanto: escopo de TCC solo não justifica a complexidade extra.

**Testes**
- Toda lógica de domínio nova (scheduler de metrônomo, scoring, futuro DTW) deve ter teste unitário JUnit mínimo antes de ser considerada pronta. As pastas `app/src/test` e `app/src/androidTest` já existem para isso.

**Commits e branches**
- Conventional Commits (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `build:`, `chore:`) — já usado informalmente no histórico do repo, manter formalizado.
- Uma branch por funcionalidade (ex.: `feature/metronomo`), merge via PR para `main`. Sem commit direto na `main` para mudanças não-triviais.
- Nunca incluir a linha `Co-Authored-By: Claude ...` nas mensagens de commit deste projeto.
- **Início de sessão:** antes de começar a trabalhar em código, verificar se o repositório local está atualizado com o GitHub (`git fetch` + comparar com o remoto). Se houver commits novos no remoto que ainda não estão local, avisar e sincronizar (pull/rebase) antes de seguir.

**Modo Planejamento (dev)**
- Mesmo fluxo planejar → validar → executar das seções de escrita: para módulo novo ou mudança arquitetural, apresente um plano técnico curto e espere aprovação antes de gerar código extenso. Para bug pontual ou ajuste pequeno e óbvio, pode implementar direto.

**Rigor técnico**
- Nunca presumir que uma API do MediaPipe/AndroidX/Compose existe ou se comporta de certa forma — conferir contra a versão travada em `app/build.gradle.kts`/`gradle/libs.versions.toml` antes de usar.

**Prioridades atuais** (lacunas identificadas em relação ao plano de trabalho do TCC)
- Ritmo/beat: metrônomo configurável (BPM + tipo de batida) e, depois, sincronização com a transferência de peso.
- Scoring real de sessão (hoje mockado em `TrainingScreen`/`MockRepository`).
- Unificar as constantes de threshold de postura hoje duplicadas entre `PostureValidator.kt`, `OverlayView.kt` e `TrainingScreen.kt`.


