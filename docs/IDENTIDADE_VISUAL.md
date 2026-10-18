# Identidade visual das telas internas

Referência: captura fornecida pelo responsável pelo projeto em 18/10/2026.
A interface usa fundo escuro, superfícies levemente elevadas, cantos arredondados,
lavanda nas ações e verde para identificar eventos gratuitos.

## Paleta

| Uso | Cor |
| --- | --- |
| Fundo | `#0C0914` |
| Cartões e campos | `#16121E` |
| Contornos | `#2B2536` |
| Ações e seleção | `#B397FF` |
| Fundo de etiquetas | `#2D2047` |
| Texto principal | `#FAF8FF` |
| Texto secundário | `#A39AAF` |
| Gratuito | `#5AE6A4` sobre `#123627` |

Os contrastes calculados de texto secundário/superfície, lavanda/superfície,
texto escuro/botão e verde/etiqueta são, respectivamente, 6,84:1, 7,71:1,
8,25:1 e 8,40:1. Informação de preço também aparece por texto, sem depender da cor.

## Componentes

- Tipografia nativa sans-serif e sans-serif-medium, sem novos downloads ou dependências.
- Títulos de 26–28 sp, títulos de seção de 18–20 sp e informações auxiliares de 12–14 sp.
- Margens internas de 20 dp; campos com cantos de 14 dp e botões com cantos de 16 dp.
- Cartões com contorno discreto, miniatura arredondada, categoria e preço em etiquetas.
- Ações principais preenchidas; ações auxiliares com superfície escura e contorno.
- Navegação inferior com destaque lavanda na aba selecionada.
- Capa ampla e arredondada nos detalhes; notas individuais em cartões de superfície.
- Formulários, perfil, relatórios, filtros e mapa seguem a mesma paleta.

A página inicial mostra o primeiro nome da conta e destaca o primeiro evento
encontrado na consulta atual. Não são inventados eventos, localização, contagens
de interessados, notificações ou status de transmissão ao vivo da referência.

## Isolamento de login e cadastro

O tema `Theme.Perto.Interno` é aplicado somente às Activities internas no Manifest.
Login e cadastro de conta continuam usando o tema original. Os arquivos originais
de cores, temas diurno/noturno, campo compartilhado e layouts e código dessas duas
telas foram comparados por SHA-256 com o estado anterior e permaneceram iguais.
O formulário de cadastro de eventos pertence à área interna e recebeu o novo tema.

Os novos componentes ficam em `colors_perto.xml`, `styles_perto.xml`, `perto_*.xml`
e `spinner_perto.xml`, sem modificar os equivalentes usados pela autenticação.
Não há mudanças nos dados, nas regras do Firestore ou nas versões das dependências.

## Validação

Foram verificados XML, IDs de componentes, isolamento do tema, compilação e testes
unitários. A conferência visual no emulador não pôde ser concluída: apesar de aparecer
conectado no ADB, ele não disponibilizou os serviços `package` e `input`, e a captura
de tela falhou. O APK precisa de revisão no aparelho, inclusive com fonte ampliada.

Conferir Início/Eventos, filtros, Favoritos, Mapa, Relatórios, Perfil, edição de perfil,
cadastro/edição de evento, detalhes, avaliações e seleção de localização. Validar
textos longos, estados vazios e de erro, rotação e área segura da câmera.
