# Perto Android

Aplicativo de descoberta e divulgação de eventos. Kotlin, Activities/Fragments,
layouts XML e View Binding. A interface permanece em `ui/`; dados, validações e
operações de autenticação/perfil ficam em `model/`, sem camadas intermediárias.

## Autenticação e perfis

- Firebase Authentication: e-mail e senha, recuperação de senha e sessão persistente.
- Cloud Firestore: documento `usuarios/{uid}` com `nome`, `email`, `tipo`,
  `endereco`, `cnpj` e `telefone`. A senha nunca é gravada nesse documento.
- Tipos de conta: `usuario` e `organizador`. CNPJ e telefone são opcionais para
  organizadores; no perfil comum são gravados como strings vazias.
- O aplicativo só abre o conteúdo principal após consultar o perfil no servidor.
  Falha de consulta permite tentar novamente ou sair. Conta autenticada sem perfil
  é encaminhada à conclusão do cadastro, inclusive após reiniciar o aplicativo.
- Se a gravação do perfil falhar depois da criação da credencial, não é necessário
  criar outra conta: repita o salvamento ou entre com a credencial já criada.
- Edição de nome e endereço para todos; CNPJ e telefone para organizadores. E-mail e tipo de conta permanecem os mesmos.

## Configuração local

1. Abra o projeto no Android Studio e instale o SDK indicado em `app/build.gradle.kts`.
2. Configure o SDK em `local.properties` (arquivo local, fora do versionamento).
3. Use o `app/google-services.json` correspondente ao aplicativo Firebase.
4. No console, habilite E-mail/senha, crie o Firestore padrão e publique
   `firestore.rules`. Ações no console são realizadas pelo responsável pelo projeto.
5. Sincronize o Gradle. O projeto usa o toolchain Java definido em
   `gradle/gradle-daemon-jvm.properties`.

Compilação e testes locais no Windows:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

## Validação manual com Firebase

Os testes locais de validação não substituem estes testes de integração:

- Criar conta comum e organizador; verificar o documento com o mesmo UID no Auth.
- Tentar e-mail inválido, senhas diferentes, senha fraca e e-mail já cadastrado.
- Sair, entrar com a senha correta e tentar credenciais incorretas.
- Solicitar recuperação e concluir o fluxo pelo e-mail recebido.
- Reiniciar o aplicativo autenticado e verificar o perfil; sair e reiniciar.
- Girar a tela e colocar o app em segundo plano durante login/cadastro.
- Testar falta de conexão, nova tentativa e conclusão de cadastro sem perfil.
- No simulador de regras, confirmar que uma conta não lê nem altera o perfil de
  outra, não lista usuários e não troca seu tipo de conta na edição.

Não use dados pessoais reais nos testes. As regras de perfis são preservadas;
a criação de eventos exige publicar a versão atualizada de `firestore.rules`.


## Cadastro de eventos com capas locais

- O organizador acessa **Início → Criar evento** e informa nome, descrição,
  endereço completo, categoria, data e horário de Brasília, gratuidade ou valor.
- A capa é escolhida entre cinco imagens incluídas no APK: música, gastronomia,
  esportes, cultura e comunidade. A categoria e a capa são escolhas independentes.
- Não há upload, URLs de imagens nem dependência de Firebase Storage.
- A coleção `eventos/{id}` armazena `nome`, `descricao`, `categoria`, `endereco`,
  `inicioMillis`, `valorCentavos`, `capa` e `organizadorId`. O preço é um inteiro
  em centavos; data e horário são convertidos para um instante em milissegundos.
- O identificador textual da capa é estável; nunca são gravados IDs numéricos
  de recursos Android no Firestore. Veja [capas e prompts](docs/CAPAS_EVENTOS.md).
- A transação confere o perfil de organizador e usa o mesmo ID nas novas tentativas
  do formulário. Uma repetição idêntica de uma gravação já concluída é aceita.
- O painel mostra uma primeira lista de até 50 eventos próprios, ordenada por data
  dentro do conjunto retornado. Paginação será uma etapa futura; a consulta para usuários comuns está descrita abaixo.
- O endereço continua textual; o ponto opcional no mapa é descrito na seção de
  localização abaixo.

### Ação necessária no Firebase

Publique o arquivo completo `firestore.rules` em **Firestore Database → Regras**.
Não é necessário criar a coleção manualmente ou configurar Storage.
As regras de eventos liberam leitura autenticada e criação somente por um perfil
organizador, com o próprio UID e uma das cinco capas permitidas. A exclusão definitiva de eventos permanece bloqueada; edição e cancelamento
exigem a versão de regras descrita na seção de gestão abaixo. As regras não foram executadas em
emulador ou publicadas pelo agente; devem ser verificadas no simulador do console.

### Roteiro de teste após publicar as regras

1. Entre com um perfil organizador; confira o botão **Criar evento** no Início.
2. Selecione cada uma das cinco capas; confirme que apenas uma fica marcada.
3. Publique um evento gratuito e um pago; confira a capa e o preço na lista.
4. No Firestore, confira `capa` textual e `valorCentavos` inteiro.
5. Teste nome/descrição/endereço vazios, data passada e valor inválido.
6. Gire o aparelho durante a edição e durante a publicação.
7. Teste falta de conexão e repetição do envio sem criar duplicatas.
8. Confirme que o usuário comum não recebe o botão de criar.
9. No simulador de regras, teste criação sem autenticação, por usuário comum,
   com UID alheio e com capa desconhecida; todas devem ser negadas.
10. Confirme que atualizações/exclusões de eventos e leitura de perfis alheios
    continuam negadas.


## Lista pública e detalhes

- No Início, o usuário comum vê eventos futuros em ordem crescente de data, com
  carregamento por páginas. A consulta utiliza o campo `inicioMillis`, sem índice composto.
- **Atualizar eventos** refaz a consulta. Falhas de conexão e listas vazias possuem
  mensagens próprias. A seleção não é por proximidade geográfica nesta etapa.
- Organizadores mantêm a lista de seus próprios eventos e o botão de criação.
- Tocar em qualquer card abre os detalhes: capa local, nome, categoria, horário
  de Brasília, preço, endereço e descrição completa.
- O ID do documento é usado na navegação, não é gravado como um campo adicional.
  Os detalhes são consultados novamente no servidor a cada abertura da tela.
- Falhas permitem nova tentativa; documento ausente apresenta evento indisponível.
- As regras já publicadas para eventos permitem as consultas desta etapa.
  Não é necessária uma nova configuração no Firebase.

Validação no aparelho: crie um evento futuro com o organizador, entre com um usuário
comum, confira a lista e abra o card. Verifique a descrição completa, capa e preço.
Teste também o botão de atualização, retorno dos detalhes, rotação da tela, falta
de conexão e nova tentativa. Eventos passados não devem aparecer na lista comum;
eles continuam disponíveis na lista do organizador. A consulta geográfica está
descrita na seção de mapas abaixo.


## Busca e filtros de eventos

A descoberta para o usuário comum permite combinar:

- Trecho do nome, ignorando acentos, maiúsculas e espaços repetidos.
- Uma ou mais categorias do evento (independentes da capa), ou todas as categorias.
- Todos os eventos, somente gratuitos ou somente pagos.

Enter ou a tecla de busca confirma o nome, sem botão de pesquisa. O funil abaixo do
campo abre o painel de categorias e preço. **Aplicar filtros** confirma as escolhas;
**Limpar filtros** restaura categorias/preço e preserva o nome digitado. Cancelar
descarta as mudanças do painel. A lista continua restrita a eventos futuros. Os critérios aplicados
sobrevivem à rotação, e o retorno dos detalhes preserva os cards já carregados.

### Paginação e limites

O Firestore consulta até 50 documentos por vez, ordenados por data, e aplica o
recorte de eventos futuros no servidor. Nome, categoria e preço são comparados no Model em cada
página. O cursor avança pelo último documento consultado, inclusive em páginas
sem correspondências. **Carregar mais** continua a mesma busca sem descartar os
resultados anteriores. Somente após esgotar a consulta a tela informa ausência
definitiva de resultados. Não há limite total de 50 eventos pesquisáveis.

Essa escolha mantém os registros existentes e dispensa novos índices e serviços
externos, mas pode ler eventos sem correspondência para encontrar resultados.
Para volumes grandes, pesquisa no servidor e índices específicos deverão ser
reavaliados. O painel de eventos próprios do organizador também possui paginação,
conforme descrito na seção de histórico abaixo; os filtros são da descoberta para o usuário comum.

Não é necessário publicar novas regras, alterar documentos existentes nem criar
índices para esta etapa.

### Verificação no aparelho

- Buscar parte do nome com e sem acentos; testar nome inexistente.
- Combinar categoria e preço; testar gratuitos e pagos separadamente.
- Selecionar várias categorias com cada uma das três opções de preço.
- Limpar os critérios, girar a tela e voltar dos detalhes.
- Quando houver mais de 50 eventos futuros, continuar por **Carregar mais**;
  uma página vazia de correspondências não deve encerrar uma busca com continuação.
- Testar erro de conexão ao continuar: os cards anteriores permanecem disponíveis.


## Interesse e favoritos

- Nos detalhes, **Tenho interesse** registra o evento em
  `usuarios/{uid}/interesses/{eventoId}`. O documento contém somente `eventoId`
  e `criadoEm`, gerado pelo servidor. O ID do evento evita duplicações.
- O estado é lido do servidor e só muda visualmente após a operação confirmada.
  Novos interesses são permitidos apenas para eventos futuros. Marcações antigas
  podem ser removidas, inclusive se o evento não existir mais.
- A aba **Favoritos** consulta páginas de 20 marcações, das mais recentes para
  as mais antigas, e recupera os dados atuais de cada evento. Uma referência
  indisponível mantém um botão de remoção, sem exibir informações antigas.
- O usuário abre os detalhes pelo card ou remove o favorito diretamente na lista.
  O retorno dos detalhes atualiza a lista. Falhas mantêm os resultados anteriores.
- A tarefa de alteração permanece no Model durante rotações; callbacks antigos
  não atualizam uma View destruída ou outra conta.

### Ação necessária no Firebase

Publique novamente o arquivo completo `firestore.rules` em **Firestore Database →
Regras**, incluindo `match /interesses/{eventoId}` dentro de `match /usuarios/{uid}`.
As permissões são exclusivas do proprietário da conta; o servidor valida a data
futura e o horário da criação da marcação. Não é necessário criar coleções,
alterar eventos existentes, configurar índices compostos ou ativar Storage.
O agente não publicou nem executou essas regras no console/emulador.

### Teste após publicar as regras

1. Entre com uma conta comum, abra um evento futuro e toque em **Tenho interesse**.
2. Abra Favoritos, confira a capa e os dados e volte aos detalhes pelo card.
3. Remova o interesse nos detalhes e confira a atualização da aba ao voltar.
4. Marque novamente e remova diretamente pela lista de Favoritos.
5. Reinicie o aplicativo e confira a persistência da marcação.
6. Entre com outra conta e confirme que ela não recebe os favoritos da primeira.
7. Teste rotação e falta de conexão durante a leitura e alteração do interesse.
8. No simulador de regras, confirme que acesso a interesses de outra conta,
   edição de marcações e criação de interesse em evento iniciado são negados.

A contagem pública de interessados e os relatórios do organizador serão tratados
em outra etapa; não há permissão para consultar os interesses de outras contas.


## Edição e cancelamento pelo organizador

- O proprietário abre o card de seu evento e encontra **Editar evento** e
  **Cancelar evento** nos detalhes. As ações são permitidas para eventos futuros
  e ativos. O Model e as regras do servidor também conferem proprietário e perfil.
- A edição reutiliza o formulário de cadastro, mantendo o ID do documento e os
  interesses associados. É possível alterar capa, nome, descrição, categoria,
  endereço, data, horário e valor, com as mesmas validações de cadastro.
- O cancelamento exige confirmação e grava `cancelado: true`, sem excluir o
  evento. Não há reativação nesta etapa. Os registros são preservados no Firestore, mas ficam ocultos
  nas listagens do organizador e nos favoritos.
- As buscas omitem cancelados ao consultar/atualizar os resultados. Não há listener
  em tempo real: uma lista já aberta precisa ser atualizada para refletir mudanças.
- Novos interesses em cancelados são bloqueados; interesses existentes podem ser
  removidos. Os detalhes e favoritos consultam os dados atuais do evento.
- Documentos antigos sem `cancelado` são considerados ativos. Não é necessário
  alterar os eventos existentes: o campo é adicionado ao criar, editar ou cancelar.
- Tarefas de gravação são vinculadas ao UID e ao ID do evento para evitar consumir
  a confirmação de outra tela/conta durante uma recriação da interface.

### Ação necessária no Firebase

Publique o arquivo **completo** `firestore.rules`, preservando os blocos de perfis
e interesses. A versão nova aceita o campo opcional `cancelado`, permite editar
somente eventos futuros do próprio organizador, permite cancelamento sem mudar
outros campos e impede a criação de interesses em eventos cancelados. A exclusão
física permanece negada. As regras não foram publicadas nem executadas no
console/emulador pelo agente.

### Teste após publicar as regras

1. Com o organizador, abra um evento futuro e altere capa, preço e descrição.
2. Com uma conta comum, atualize a busca e confira os novos dados nos detalhes.
3. Confira que um favorito anterior continua apontando para o mesmo evento.
4. Abra o cancelamento e escolha **Manter evento**; nada deve mudar.
5. Confirme o cancelamento; confira que o evento sai da busca após atualização.
6. Confira que o evento cancelado desaparece de Meus eventos e dos Favoritos após atualização.
7. Confira no Firestore que o documento foi preservado; criar um novo interesse no cancelado deve ser negado.
8. Confirme que eventos iniciados ou de outro organizador não oferecem gestão.
9. Teste rotação e falha de conexão ao editar e cancelar.
10. No simulador de regras, teste tentativa de alterar o dono, editar evento
    cancelado, reativar um cancelado ou cancelar alterando outros campos: negadas.


## Localização e mapa

- Cadastro/edição inclui **Escolher localização no mapa**. O organizador pode buscar
  um endereço, tocar no mapa ou usar a localização do aparelho e ajustar o ponto.
  Buscar um endereço apenas centraliza o mapa; a seleção deve ser confirmada.
- São armazenados apenas `latitude` e `longitude` públicas do evento, como números
  opcionais. Sem ponto, o evento continua na lista e não recebe marcador no mapa.
  Alterar o endereço manualmente remove a escolha anterior para evitar divergência.
- A aba Mapa consulta eventos futuros e ativos, por páginas de 50, com marcadores
  que abrem os detalhes. Registros antigos aparecem após adicionar o ponto na edição.
- A área de referência pode ser escolhida por toque, cidade/endereço ou posição do
  aparelho. Há raios de 5, 10, 25 e 50 km, em linha reta, aplicados aos eventos já
  carregados. **Carregar mais** continua a consulta; não há índice geoespacial nesta
  entrega. Uma futura escala maior exigirá rever essa estratégia de leitura.
- Localização é solicitada somente pelo botão **Minha localização**, incluindo
  autorização em tempo de execução. Localização aproximada é aceita. Não existe
  permissão de segundo plano nem gravação da posição do usuário no Firestore.
- Se a permissão for negada, o GPS estiver desativado ou o endereço não for
  encontrado, a escolha manual continua disponível. A posição tem espera máxima
  de 25 segundos, e a solicitação é cancelada quando a tela deixa de estar visível.
- O endereço é consultado pelo Geocoder do Android, em thread de IO. O serviço
  depende do dispositivo e pode estar indisponível; não é um autocomplete de endereços.
- Os detalhes oferecem abertura do ponto em outro aplicativo de mapas, sem
  necessidade de obter a localização do usuário.

### Biblioteca, rede e segurança

Leaflet 1.9.4 é incluído no APK, com JavaScript e CSS cujos hashes foram conferidos
contra a página oficial. Licença em `app/src/main/assets/mapa/LEAFLET-LICENSE.txt`.
As bases do mapa vêm por HTTPS de `tile.openstreetmap.org`, com atribuição visível,
User-Agent identificando o Perto e cache HTTP padrão da WebView. Não há download de
áreas para uso offline, prefetch em lote nem chave Google Maps. O serviço é de
melhor esforço e deve ser reavaliado antes de escalar o aplicativo.

A WebView serve arquivos locais por uma origem HTTPS interna, bloqueia acesso a
arquivos e conteúdo, navegação remota e rede fora das imagens do provedor. A ponte
expõe somente seleção de coordenadas válidas e abertura de IDs já carregados.
Textos do banco são codificados como JSON e inseridos no popup como texto, sem
HTML dinâmico. A WebView e sua ponte são destruídas junto com a tela.

Fontes: [Leaflet](https://leafletjs.com/download.html),
[política de tiles OpenStreetMap](https://operations.osmfoundation.org/policies/tiles/),
[LocationManagerCompat](https://developer.android.com/reference/androidx/core/location/LocationManagerCompat).

### Ação necessária no Firebase

Publique a versão completa de `firestore.rules` com os campos opcionais `latitude`
e `longitude`. As regras validam o par completo e os limites geográficos. Eventos
existentes sem coordenadas continuam aceitos. Não são necessários migração,
índices compostos, Storage, chave de mapas ou faturamento de um provedor de mapas.
O agente não publicou nem executou as regras no console/emulador.

### Validação no aparelho após publicar

1. Edite um evento futuro, escolha e confirme um ponto no mapa e salve.
2. Entre como usuário comum, abra Mapa e carregue o evento. Toque no marcador e
   depois em **Ver detalhes**. Confira a abertura da localização nos detalhes.
3. Consulte um endereço com cidade, altere a referência e teste os raios disponíveis.
4. Teste localização aproximada, permissão negada, GPS desativado e demora na posição.
5. Teste falta de conexão nos mapas e no Firestore e a seleção manual de localização.
6. Gire as telas durante a escolha do ponto; a seleção deve permanecer.
7. Verifique que eventos sem ponto, cancelados ou iniciados não recebem marcadores.
8. No simulador de regras, teste pares incompletos e latitude/longitude fora dos
   limites: devem ser negados. Campos ausentes ou ambos nulos são aceitos.

O mapa e o GPS ainda precisam de teste no dispositivo; testes unitários cobrem
validação de coordenadas, compatibilidade de eventos sem ponto e cálculo de distância.


### Edição de perfil

Na aba Perfil, **Editar perfil** permite atualizar nome e endereço; organizadores
podem também alterar CNPJ e telefone. E-mail e tipo de conta são preservados pela
transação no Model e pelas regras existentes. A tela confirma o sucesso após a
resposta do servidor, mantém os campos em caso de erro e reconecta a gravação ao
voltar do segundo plano ou girar o aparelho. Não há mudança nas regras do Firebase.

Validação manual: alterar cada tipo de perfil, salvar e voltar à aba Perfil;
reiniciar a sessão e conferir a persistência; testar nome curto, rotação durante
a edição e falta de conexão ao salvar (transações precisam de internet).


### Histórico completo do organizador

O painel **Meus eventos** consulta páginas de até 50 documentos da própria conta.
**Carregar mais** mantém os resultados anteriores e avança pelo cursor do último
documento recebido. A consulta usa a ordenação padrão pelo identificador do documento,
sem índice composto adicional. A interface reordena todos os eventos já carregados
por data, com desempate pelo identificador; eventos de uma página seguinte podem
aparecer antes dos anteriores. O texto da tela informa quando a consulta está incompleta.
Eventos cancelados ficam ocultos na listagem; eventos antigos não cancelados continuam acessíveis. Atualizar ou retornar dos detalhes
reinicia a consulta para refletir edições e cancelamentos. Falhas ao carregar outra
página preservam os cards e o cursor, permitindo repetir **Carregar mais**.

Não é necessário alterar o Firebase. Verificação manual: em uma conta com mais de
50 eventos, carregar páginas até o fim, conferir ausência de duplicação e a ordenação,
abrir detalhes de um evento das páginas seguintes e testar falha de conexão durante
a continuação. Contas vazias e com poucos eventos também devem ser verificadas.


### Confirmação de realização do evento

Nos detalhes de um evento iniciado, o proprietário pode usar **Marcar como realizado**
e confirmar que o evento terminou. O campo opcional `realizado` vale `false` em
registros antigos e só pode mudar para `true`, sem alterar outros campos, depois
do início. A regra usa o horário do servidor; o horário local apenas orienta a tela.
Não há término automático: passar da data de início continua significando apenas
que o evento começou. Cancelados não podem ser marcados como realizados.

A confirmação usa uma transação no Model, reconectada na Activity depois de rotação
ou retorno do segundo plano. A repetição após perda da resposta é idempotente.
O status aparece nos detalhes, Meus eventos e Favoritos; interesses existentes
podem ser removidos. A descoberta omite eventos realizados. Avaliações estão disponíveis conforme a seção abaixo. A edição
de informações durante a realização continua pendente para outro incremento.

**Ação necessária no Firebase:** publicar o conteúdo completo de `firestore.rules`
antes de testar este APK. Novos eventos também passam a enviar `realizado: false`,
campo que as regras anteriores não aceitam. Não precisa alterar os documentos
existentes, criar índices nem ativar serviços pagos. O agente não publicou as regras.

Validação manual após publicar:
1. Criar um evento para poucos minutos à frente; antes do início, conferir que não
   existe a ação de realização e que editar/cancelar continuam funcionando.
2. Após o início, atualizar os detalhes como proprietário e confirmar a realização.
3. Conferir o status nos detalhes, na lista do organizador e nos favoritos de outra conta.
4. Verificar que eventos cancelados não oferecem a ação e que outra conta não pode concluí-los.
5. Testar rotação, segundo plano, falha de conexão e repetição da operação.
6. No simulador de regras, negar conclusão antecipada, por outro organizador,
   acompanhada de mudança de nome, em cancelados ou reversão de `realizado`.

Os testes locais cobrem propriedade, horários e estados do Model. As regras e a
interface ainda precisam de validação no Firebase e no dispositivo.


### Eventos cancelados nas listas

Cancelados ficam ocultos em Meus eventos, Favoritos, descoberta e mapa. O filtro
é aplicado no Model após ler cada página, mantendo o cursor e a indicação de
continuação pelos documentos originais. Uma página composta só por cancelados
não encerra a consulta: Carregar mais continua disponível. Os documentos e as
marcações de interesse são preservados no Firestore; não há exclusão de dados.
Esta alteração não exige novas regras ou índices no Firebase.


### Avaliações antes ou depois da realização

Nos detalhes de qualquer evento não cancelado, **Avaliar / Ver avaliações** abre
um formulário de nota inteira de 1 a 5 com comentário opcional de até 1000 caracteres.
Apenas contas do tipo usuário podem enviar; organizadores consultam as avaliações.
O proprietário não pode avaliar o próprio evento. Não há verificação de presença
física, ingresso ou participação: esse controle não foi definido para esta etapa.

Cada avaliação fica em `eventos/{eventoId}/avaliacoes/{uid}` com `nota`, `comentario`
e `atualizadoEm` (horário do servidor). Salvar novamente atualiza o mesmo documento.
Não são copiados nome, e-mail ou telefone do perfil para as avaliações. As avaliações
podem ser lidas por contas autenticadas; o UID permanece identificável no caminho
do documento, portanto não se trata de anonimato. A tela informa que o conteúdo é
visível para outras contas. A consulta usa páginas de 20 pela ordenação padrão de
identificador, sem índice composto.

O Model reconfere evento e tipo de conta em uma transação. A View preserva os campos
na rotação e reconecta a gravação após segundo plano; consultas antigas da lista são
descartadas ao reiniciá-la. Erros preservam o formulário, e cada consulta pode ser
repetida. Eventos realizados podem ser acessados pelos Favoritos do usuário e por
Meus eventos do organizador. Não foi criada uma nova busca de eventos passados.

**Ação necessária no Firebase:** publicar o conteúdo completo de `firestore.rules`
antes do teste. As regras liberam a subcoleção em eventos não cancelados,
independentemente da data e do campo realizado, restringem cada escrita ao próprio UID com tipo usuário e validam
nota, comentário, campos permitidos e timestamp. Sem exclusão nesta etapa. O agente
não publicou nem executou as regras no console ou no emulador. Não é necessário
criar coleções manualmente, índices compostos ou serviços pagos.

Verificação no aparelho após publicação:
1. Como usuário, abrir um evento futuro na descoberta e salvar uma avaliação.
   Repetir com evento iniciado e com evento marcado como realizado.
2. Tentar salvar sem nota, depois salvar nota e comentário; reabrir e conferir os dados.
3. Alterar a nota e confirmar no Firestore que existe apenas um documento para o UID.
4. Como organizador, consultar o evento e verificar que não existe formulário de envio.
5. Testar rotação durante a edição e gravação, segundo plano e conexão indisponível.
6. Com mais de 20 avaliações, continuar as páginas e testar erro/repetição da consulta.
7. No simulador de regras, negar escrita com outro UID, organizador, nota 0 ou 6,
   comentário acima do limite, campos extras ou horário manual. Negar avaliações
   de evento cancelado e alterações em avaliações alheias. Permitir nota válida
   de usuário comum em evento futuro, iniciado ou realizado.

Os testes locais verificam os limites de nota/comentário, permissões de tipo e
propriedade e estados do evento no Model. Interface e regras dependem da validação
no dispositivo e no Firebase.


O acesso **Avaliar evento** fica abaixo de Sobre o evento nos detalhes e está
disponível antes e depois do evento, por solicitação do responsável pelo projeto.
Não é necessário marcar o evento como realizado para avaliar. Organizadores
acessam a mesma tela apenas para consulta. É necessário republicar as regras
atualizadas para liberar a avaliação antecipada no servidor.


### Relatório de eventos cadastrados

Na conta de organizador, **Relatórios** apresenta totais
de cadastrados consultados, agendados, iniciados, realizados e cancelados. Cada
evento ocupa uma única situação. Iniciado significa que o início passou, mas o
organizador ainda não confirmou a realização. A classificação usa um horário de
referência único para todas as páginas, exibido na tela.

O relatório lê apenas eventos associados ao UID autenticado, em páginas de 50,
incluindo cancelados exclusivamente para a contagem. Listagens comuns e Favoritos
continuam ocultando cancelados. A contagem parcial é identificada até esgotar as
páginas por **Carregar mais**; erros preservam os números e o cursor anteriores.
Atualizar, reabrir ou girar a tela reinicia a consulta para refletir os dados atuais.
As páginas não constituem um snapshot atômico do banco: alterações em outro aparelho
durante a consulta podem exigir uma atualização ao final.

O cálculo fica no Model (`ResumoEventos`); Activity e XML apresentam o resultado.
Este incremento cobre o relatório de cadastros/situações. Não calcula receita nem
presença. A tela de avaliações apresenta apenas as notas e comentários individuais; o relatório de interesses segue pendente.
Não exige novas regras, coleções, índices ou ações no console Firebase.

Verificação manual: abrir como organizador sem eventos e com eventos em cada
situação, conferir a soma dos grupos, testar mais de 50 registros e continuação
após falha de conexão. Confirmar que o botão não aparece para usuário comum e que
cancelados permanecem ocultos em Meus eventos e Favoritos. Os testes locais cobrem
os limites de horário, lista vazia e contagem exclusiva por situação.

O resumo agregado de avaliações foi removido por solicitação do usuário. A tela
mantém o formulário de avaliação, a lista de notas e comentários e a paginação.


### Validação e mensagens do login

O formulário valida os dois campos na mesma tentativa: e-mail vazio/mal formatado
e senha ausente são informados em mensagens persistentes abaixo de cada campo,
com foco no primeiro inválido. Ao editar um campo, sua mensagem anterior é removida.
Ao iniciar nova tentativa, as mensagens anteriores são limpas.

Rejeições de credenciais retornadas pelo Firebase (`ERROR_WRONG_PASSWORD`,
`ERROR_USER_NOT_FOUND`, `ERROR_INVALID_CREDENTIAL`, `ERROR_INVALID_LOGIN_CREDENTIALS`)
mostram **E-mail ou senha incorretos.** abaixo dos campos. O app não presume qual
credencial está errada quando o serviço rejeita o conjunto. Formato inválido de
e-mail retornado pelo serviço é associado ao e-mail; rede, conta desativada,
limite de tentativas e falhas de acesso ao perfil preservam suas mensagens próprias.
A recuperação de senha valida somente o e-mail, sem exigir senha.

O Model verifica os campos sem remover espaços da senha nem aplicar a política
de criação ao login. A View apresenta as mensagens e mantém o bloqueio de envios
duplicados durante a autenticação. Não exige ação ou mudança no Firebase.

Verificação manual: ambos os campos vazios; e-mail mal formatado com senha vazia;
e-mail válido e senha vazia; e-mail/senha rejeitados pelo serviço; login válido;
falta de internet; edição depois de um erro; recuperação sem senha; rotação durante
a autenticação. Os testes locais verificam a validação por campo. As respostas reais
do Firebase e a apresentação precisam de teste no dispositivo.


### Avaliações nos detalhes do evento

A seção **Sobre o evento** é seguida pelo botão **Avaliar evento** e pela lista
completa das avaliações. A consulta usa as páginas de 20 já previstas nas regras,
avançando automaticamente até o fim. Não existe corte nos primeiros 20 registros.
Sem avaliações, a tela informa que o evento ainda não recebeu avaliações. Falhas
mostram erro e nova tentativa, preservando os cards já carregados e o cursor.
Respostas anteriores são descartadas ao sair da tela ou reconsultar o evento.

O botão **Tenho interesse**, próximo ao título, possui coração de contorno e muda
para preenchido após confirmar a marcação. A ação continua permitindo remover
um interesse existente. Disponibilidade e restrições de eventos cancelados,
iniciados e realizados permanecem as mesmas.

Salvar uma avaliação retorna aos detalhes somente depois da confirmação do servidor.
A retomada consulta novamente o evento e todas as avaliações, incluindo a nova nota
ou a edição existente. O formulário permanece aberto e conserva os campos se a
gravação falhar. Não foi reintroduzido o resumo agregado nem o botão de atualização.
Não exige ação no Firebase: utiliza a subcoleção e as regras já publicadas.

Verificação manual: abrir um evento sem avaliações; enviar a primeira nota e
conferir o retorno aos detalhes; editar a nota e conferir que não foi duplicada;
testar vários comentários, mais de 20 avaliações, queda de conexão na paginação,
retorno do segundo plano e rotação. Conferir ordem Sobre → Avaliar → lista e o
coração antes/depois de marcar e remover interesse. A compilação e os testes locais
não substituem a verificação da interface e dos dados no aparelho.


### Pesquisa pelo teclado e painel de filtros

A busca é confirmada por Search/Done do teclado virtual ou Enter do teclado físico,
com proteção contra repetição da mesma tecla. Não há botão Buscar eventos. Uma
nova confirmação substitui a consulta anterior, inclusive enquanto ela está em
andamento; callbacks antigos são descartados. O teclado é recolhido ao confirmar.

O funil de 48 dp fica logo abaixo do campo, possui descrição de acessibilidade e
abre as categorias disponíveis como seleção múltipla, mais preço Todos/Gratuitos/
Pagos. Sem categoria selecionada, todas são incluídas. Nome e preço precisam
corresponder ao evento; basta pertencer a uma das categorias selecionadas. Os
critérios aplicados aparecem junto ao funil e são preservados ao girar a tela.
O diálogo é fechado ao destruir a View, sem reter referências ao Fragment.

Se uma página ainda não trouxe resultados, a consulta avança automaticamente até
encontrar correspondências ou esgotar os eventos futuros. Só então pode mostrar a
mensagem de ausência. Quando já existem resultados, Carregar mais continua a busca.
Não exige novas regras, índices ou ações no Firebase. Validação no aparelho: teclado
virtual/físico, combinação de vários filtros, cancelar/limpar o painel, nenhum
resultado, mais de 50 documentos, falha de conexão e rotação. Os testes locais
cobrem nome sem acentos, múltiplas categorias, preço e exclusão de cancelados.


### Navegação da conta do organizador

Após validar o perfil no servidor, a barra inferior do organizador apresenta
exatamente **Eventos**, **Relatórios** e **Perfil**. A aba selecionada é salva
na recriação da Activity; um fragmento restaurado da mesma tela é preservado.
Usuários comuns continuam com Início, Mapa, Favoritos e Perfil.

- Eventos reutiliza a listagem dos próprios eventos, cadastro, detalhes, edição,
  cancelamento e confirmação de realização. Cancelados continuam ocultos na lista.
- Relatórios apresenta diretamente os indicadores existentes em um Fragment,
  com paginação, indicação de contagem parcial, atualização e tratamento de erro.
  Somente os dados ligados ao UID atual são contabilizados. Cancelados entram só
  na contagem. Não foi acrescentado resumo agregado de avaliações.
- Perfil reutiliza a consulta e a edição dos dados da conta e a saída da sessão.

O antigo botão de relatório dentro de Eventos foi removido e a Activity exclusiva
foi substituída pela aba Relatórios. O cálculo continua no Model, e os callbacks
do Fragment validam a View, a consulta e a conta antes de atualizar a interface.
Não exige mudança de regras, dados, índices ou outra ação no Firebase.

Verificação manual: entrar com organizador e conferir as três abas, abrir detalhes
e gerenciar um evento, consultar relatórios e editar perfil; girar a tela em cada
aba e retornar do segundo plano. Sair e entrar como usuário comum para conferir
os quatro menus desse perfil. Testar erro na consulta de perfil e relatório.


### Identidade visual das telas internas

A referência enviada em 18/10/2026 orienta a nova paleta escura com lavanda,
cartões compactos, capas arredondadas, etiquetas de categoria/preço e navegação
com seleção destacada. Formulários, mapa, perfil, relatórios, detalhes e avaliações
compartilham um tema exclusivo. Login e cadastro de conta permanecem com o visual
original. A página inicial usa o nome real da conta e um evento da consulta como
destaque. Nenhum dado ilustrativo da referência foi gravado no sistema.

Detalhes de paleta, componentes, isolamento e validação estão em
[docs/IDENTIDADE_VISUAL.md](docs/IDENTIDADE_VISUAL.md). Não exige ação no Firebase.
