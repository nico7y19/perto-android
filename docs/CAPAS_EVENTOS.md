# Capas locais de eventos

Cinco capas criadas com a ferramenta integrada ImageGen. Os arquivos WebP foram
reduzidos para até 768 × 512 pixels para uso no Android e somam aproximadamente
562 KiB. Não há upload, download ou Firebase Storage nesse fluxo.

| Identificador salvo no Firestore | Arquivo no aplicativo |
| --- | --- |
| `musica` | [capa_evento_musica.webp](../app/src/main/res/drawable-nodpi/capa_evento_musica.webp) |
| `gastronomia` | [capa_evento_gastronomia.webp](../app/src/main/res/drawable-nodpi/capa_evento_gastronomia.webp) |
| `esportes` | [capa_evento_esportes.webp](../app/src/main/res/drawable-nodpi/capa_evento_esportes.webp) |
| `cultura` | [capa_evento_cultura.webp](../app/src/main/res/drawable-nodpi/capa_evento_cultura.webp) |
| `comunidade` | [capa_evento_comunidade.webp](../app/src/main/res/drawable-nodpi/capa_evento_comunidade.webp) |

## Prompts usados

Cada assunto foi gerado separadamente, sem imagem de referência, sem fundo
transparente e com o seguinte prompt completo (substituindo `{subject}`):

> Create ONE landscape 3:2 event cover illustration for the Android app Perto. Subject: {subject}. Cohesive contemporary editorial illustration, bold simple forms, subtle paper grain, polished dimensional lighting, saturated accents on deep midnight navy background, welcoming and energetic, clear focal point and readable at thumbnail size. Full bleed, no border, no text, no letters, no logos, no watermarks. This is a distinct standalone asset, not a collage or contact sheet.

Assuntos:

- **musica:** an outdoor live music festival at dusk, expressive abstract stage and rhythmic sound waves, purple and indigo lights
- **gastronomia:** a lively Brazilian food fair, inviting colorful dishes, artisan bread and fresh produce, warm amber and coral
- **esportes:** a community sports day in a green urban park, running track, ball and dynamic motion, teal and lime
- **cultura:** a cultural arts festival, sculptural theater masks, ceramic forms and paint shapes, magenta and warm orange
- **comunidade:** a friendly neighborhood gathering in a sunny Brazilian town square, small diverse groups and trees, sky blue and golden yellow

## Integração

O Model armazena `capa` como um identificador textual estável. `CapasEvento`, na
View, converte esse identificador para `R.drawable`. IDs numéricos de recursos
Android não são persistidos no banco, pois podem mudar entre compilações.
