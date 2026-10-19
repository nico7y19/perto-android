# Lembretes locais

Contas de usuário recebem um aviso aproximadamente uma hora antes dos eventos favoritos. Ao marcar interesse faltando menos de uma hora, o aviso é agendado para assim que possível. O Android pode atrasá-lo para economizar bateria; eventos já iniciados não geram aviso.

A permissão é solicitada após marcar interesse no Android 13 ou superior. Negar não impede salvar o favorito. No Perfil é possível ativar/desativar os avisos e abrir as configurações do Android. Organizadores não têm essa opção.

AlarmManager usa os dados armazenados no aparelho, sem servidor de notificações e sem alterações no Firebase. Os favoritos são sincronizados ao abrir a área principal. Alterações ou cancelamentos feitos remotamente enquanto o app permanece fechado podem não ser conhecidos pelo lembrete local. Não há notificações push.

Remover o interesse confirmado pelo servidor cancela o alarme. Sair ou trocar de conta limpa os avisos locais. Reiniciar o aparelho, atualizar o app e ajustar o relógio restauram os alarmes conhecidos. Forçar a parada nas configurações do Android impede avisos até reabrir o aplicativo. Limpar dados ou reinstalar exige abrir o app novamente para sincronizar. As configurações e alarmes locais não entram no backup.

## Validação no aparelho

1. Entrar como usuário e marcar interesse num evento futuro a menos de uma hora. Autorizar as notificações e verificar um aviso com o nome/data do evento.
2. Tocar no aviso e conferir os detalhes e a navegação de volta.
3. Reabrir o app: o mesmo evento/data não deve repetir o aviso já entregue.
4. Marcar um evento com mais de uma hora de antecedência; remover interesse e verificar que o aviso não chega.
5. Desativar no Perfil; confirmar cancelamento dos alarmes. Reativar e verificar reagendamento.
6. Negar a permissão: o favorito continua salvo e o Perfil informa o bloqueio. Autorizar nas configurações e retornar ao app.
7. Reiniciar o aparelho com um lembrete futuro pendente; verificar entrega após o horário previsto, respeitando atrasos do Android.
8. Sair da conta e conferir cancelamento dos avisos; entrar como organizador e confirmar ausência das opções de lembretes.

Testes unitários cobrem o cálculo do horário e a exclusão de eventos iniciados, cancelados e concluídos. Entrega pelo sistema e navegação exigem validação em aparelho Android.
