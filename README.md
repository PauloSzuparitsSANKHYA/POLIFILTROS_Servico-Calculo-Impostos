<div align="center">
<h1>
  Template Addon
</h1><br/>


</div>  


# Objetivo
Para aumentar a consistência e a eficiência na construção de um add-on, este template foi desenvolvido com uma estrutura pré-configurada que simplifica os processos iniciais de desenvolvimento. Além disso, com algumas configurações adicionais, é possível automatizar a distribuição do add-on no Sankhya Place.

# Requisitos
Antes de iniciarmos o desenvolvimento do addon, vamos certificar que você tenha tudo o que precisa.
## 💻 Requisitos de Hardware

| Componente        | Mínimo Recomendado                     |
| :---------------- | :------------------------------------- |
| **Processador**   | Intel Core i5 ou equivalente AMD Ryzen |
| **Memória RAM**   | 16 GB                                  |
| **Armazenamento** | SSD de 120 GB ou mais                  |

> **💡 Dica:** Usar um SSD fará uma grande diferença na velocidade de compilação e na inicialização do ambiente.

## 🔧 Requisitos de Software e Conhecimento

| Item              | Detalhes                                                                                                                 |
| :---------------- | :----------------------------------------------------------------------------------------------------------------------- |
| **Sankhya OM**    | Servidor de aplicação e banco de dados (SDK)                                                                             |
| **Java**          | [JDK 8 (LTS)](https://downloads.sankhya.com.br/downloads?app=JAVA&c=1). Versões mais recentes podem não ser compatíveis. |
| **IDE**           | [IntelliJ IDEA](https://www.jetbrains.com/idea/) (Community ou Ultimate) é a recomendada.                                |
| **Gradle**        | Já vem integrado ao IntelliJ, então não é preciso instalar separadamente.                                                |
| **Docker**        | Essencial para rodar os bancos de dados de forma isolada.                                                                |
| **Conta Sankhya** | [Registro na Área de Desenvolvedores](https://www.sankhya.com.br/developers/)                                            |


# 1. Ambiente de desenvolvimento

## 1.1 Servidor de aplicação e banco de dados Sankhya

Crie uma solução e um componente addon conforme nosso guia em [Portal do Desenvolvedor Sankhya#Criar nova solução](https://developer.sankhya.com.br/docs/portal-do-desenvolvedor#criar-nova-solu%C3%A7%C3%A3o). O template estará disponível para download no mesmo formulário do Addon.

Obs.: Caso não tenha acesso ao Sankhya ID realize o cadastro da sua empresa como desenvolvedora para seguir. [Saiba mais](https://developer.sankhya.com.br/docs/como-participar-do-ecossistema-de-desenvolvimento-sankhya).

### 1.1.1.  Configuração do Banco de Dados com Docker <br><br>

Este tópico aborda como configurar um banco de dados utilizando Docker, permitindo de forma prática, de ter um banco de dados compatível com o Sankhya Om.
<br>

##### Criando um volume para persistir os dados

Para garantir a preservação dos dados da sua base de desenvolvimento, crie um volume antes de executar o container. Use o comando:<br>

* Em Oracle:
```bash
docker volume create skdev-oracle-volume
```

* Em SQL Server: 
```bash
docker volume create skdev-mssql-volume
```
<br>

##### Iniciando o container do banco de dados

Após a criação do volume é necessário inicar o container, para isso execute o comando abaixo:<br>

* Em Oracle:

```bash
docker run -d --name skdev-oracle --shm-size=1g -p 1521:1521 -p 5500:5500 -v skdev-oracle-volume:/opt/oracle/oradata sankhyaimages/skdev-oracle:1.1.0
```

* Em SQL Server:

```bash
docker run -d --name skdev-mssql -p 1433:1433 -v skdev-mssql-volume:/var/opt/mssql sankhyaimages/skdev-mssql:1.1.0
```


> ⚠️ **Importante**: A primeira inicialização pode levar de 20 a 30 minutos. Você pode acompanhar o progresso com o comando: *docker logs -f <nome-do-container>*.
Após finalizar toda a configuração, acesse o docker e verifique que seu container está em execução.

<br>

##### Conectando na base de dados 

Use estas credenciais para se conectar ao banco de dados a partir do WPM ou de um cliente de banco de dados.

| Banco          | Endereço    | Porta  | SID/Banco | Usuário   | Senha       |
| :------------- | :---------- | :----- | :-------- | :-------- | :---------- |
| **Oracle**     | `localhost` | `1521` | `XE`      | `SANKHYA` | `developer` |
| **SQL Server** | `localhost` | `1433` | `jiva`    | `SANKHYA` | `developer` |
<br>

##### Parando e reiniciando o container docker

Para interromper o ambiente de desenvolvimento, execute:<br>

* Em Oracle:
```bash
docker stop skdev-oracle
```

* Em SQL Server: 
```bash
docker stop skdev-mssql
```

Para reiniciar o ambiente, utilize: <br>

* Em Oracle: 
```bash
docker start skdev-oracle
```

* Em SLQ Server:
```bash
docker start skdev-mssql
```
---

### 1.1.2 Servidor de aplicação

Após inicializar o banco de dados, é necessário instalar e iniciar o servidor de aplicação. Para isso, acesse a [Central de Downloads Sankhya](https://downloads.sankhya.com.br/downloads?app=WildFly&c=1) e faça o download do WildFly 23.0. Após, extraia o arquivo em um local de fácil acesso (ex: C:\wildfly ou /home/user/wildfly).

Então, inicie o servidor a partir do terminal, dentro da pasta bin do Wildfly:

* Windows:
```bash
.\standalone.bat
```

* Linux:
```bash
./standalone.sh
```

**Siga as instruções detalhadas nos manuais abaixo:**
- [Manual de Instalação do Sankhya OM em Ambiente Linux](https://ajuda.sankhya.com.br/hc/pt-br/articles/360045547894-Manual-de-Instala%C3%A7%C3%A3o-Sankhya-Om-em-Ambiente-Linux#Configura%C3%A7%C3%A3odoWildfly)
- [Manual de Instalação do Sankhya OM em Ambiente Windows](https://ajuda.sankhya.com.br/hc/pt-br/articles/360045695134-Manual-de-Instala%C3%A7%C3%A3o-Sankhya-Om-em-Ambiente-Windows)

<br>

**Como Habilitar o Modo Debug?**

Para executar o Wildfly em modo debug faça:

* Windows:
```bash
.\standalone.bat --debug
```

* Linux:
```bash
./standalone.sh --debug
```

Assim, o modo debug estará ouvindo na porta 8787. Isso permite que você conecte sua IDE para depuração.
O Addon Studio já cria os arquivos de configuração necessários para executar a depuração remota.

### 1.1.3 Configuração do WPM e o Sankhya Om

1. Acesse o WPM no seu navegador: http://localhost:8080/wpm/.
2. A senha padrão no primeiro acesso é admin. Você será solicitado a alterá-la.
3. Na tela de configuração, insira os dados de conexão do banco de dados que você configurou no Docker.
4. Após a conexão, o WPM permitirá que você baixe e instale a versão desejada do Sankhya Om. Escolha sempre a versão mais recente disponível.
5. Siga o processo de instalação. Ao final, seu ambiente estará pronto!

# 2. Configurações do add-on studio

Depois de preparar o ambiente, o próximo passo é configurar o projeto do seu add-on.

## 2.1. Ajustando o arquivo settings.gradle

Este arquivo é o coração da estrutura do seu projeto. Ele define o nome raiz e os módulos que o compõem. Sem essa configuração, o Gradle não consegue montar o projeto corretamente. 
Altere os valores conforme o exemplo baixo, para configurar o nome do projeto e os módulos.
```groovy
// Define o nome raiz do projeto. Use um nome único e descritivo.
rootProject.name = 'meu-addon-incrivel'

// Inclui os módulos que fazem parte do projeto.
// 'model' é para a lógica de negócio (backend).
// 'vc' é para componentes de tela (frontend, se aplicável).
include 'model'
include 'vc'
```

> 📘 **Dica:** Escolha um nome para rootProject.name que reflita o propósito do seu add-on, como financeiro-avancado ou gestao-estoque.
O cliente do seu addon, poderá instalar apenas um addon com esse nome, então escolha com cuidado.

> ⚠️ **Cuidado**: Evite usar nomes genéricos como addon para rootProject.name, pois isso pode causar conflitos se você tiver múltiplos projetos.

## 2.2 Configurações do arquivo build.gradle

O build.gradle na raiz do projeto define as configurações globais do build. Configure os seguintes itens conforme especificado abaixo no arquivo build.gradle:
- **group**<br> Por convenção utiliza-se o seu domínio ao contrário seguido do nome da aplicação, conforme exemplo abaixo:
  ```groovy
  // Se seu site é "minhaempresa.com.br" e o addon é "addonexemplo"
  group = 'br.com.minhaempresa.addonexemplo'
  ```
- **snkmodule**
  ```groovy
  snkmodule  {
     //Caminho do servidor de aplicação Wildfly
     serverFolder = '${WILDFLY_HOME}' ?: 'C:\\wildfly'
     // Versão mínima da plataforma Sankhya que seu add-on suporta.
     plataformaMinima = "4.28"
  }
  ```

- **addon**

  Este bloco vincula seu projeto ao componente da solução registrada no Portal do Desenvolvedor.  
  ```groovy
  addon {
    // AppKey obtida ao registrar a solução no Portal do Desenvolvedor.
    appKey = "APP_KEY_INFORMADA"
    // Nome do parceiro (geralmente o nome da sua empresa).
    parceiroNome = "Minha Empresa"
  }
  ```
> ⚠️ **Importante**: O campo "appKey" é obrigatório para a geração do Addon.

- **Configuração de `dependencies` em `buildscript`**

É necessário garantir que as dependências abaixo estejam configuradas no buildscript:

```groovy
     buildscript {
    
        dependencies {
            classpath "br.com.sankhya.studio:gradle-plugin:2+"
            classpath "com.google.devtools.ksp:symbol-processing-gradle-plugin:2.0.0-1.0.24"
            classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.0"
        }
     }
```

### Segurança: 
Nunca comite sua appKey diretamente no build.gradle em repositórios públicos. Use o arquivo .env (que não é versionado) para armazenar dados sensíveis.

# 3. Gestão de dependências
O gradle já inclui as seguintes dependências por padrão.
- model:
  - mge-modelcore
  - jape
  - sanutil
  - sanws
  - dwf
  - mge-param
  - gson-2.1
  - commons-httpclient-3.0.1-snk
  - jdom
  - wildfly-spec-api
- vc:
  - dwf
  - gson-2.1
  - sanws
  - jape
  - sanutil
  - sanmodule
  - servlet-api
  - wildfly-spec-api
  - commons-httpclient-3.0.1-snk

Para adicionar outras dependências, você tem duas opções principais no build.gradle dos seus módulos (model/build.gradle ou vc/build.gradle):

- **implementation:** Usa uma biblioteca que já existe no Sankhya Om. O add-on não incluirá o .jar da biblioteca, apenas a usará em tempo de execução.
- **moduleLib:** Incorpora uma biblioteca ao seu add-on. Isso é útil quando você precisa de uma versão diferente da que existe no Sankhya Om ou uma biblioteca que não está presente.

### Exemplo prático:
```groovy
dependencies {
    // Usa a biblioteca 'bsh' que já está no monolito.
    // O JAR não será empacotado no seu add-on.
    implementation('br.com.sankhya:bsh-1.3.0:master')

    // Empacota a biblioteca 'skw-environment' na versão 1.8.2 dentro do seu add-on.
    // Isso permite usar uma versão específica, diferente da do monolito.
    moduleLib('br.com.sankhya:skw-environment:1.8.2')
}
```

# 4. Scripts/migrations
Os scripts de migração são responsáveis pela criação e alteração de tabelas, campos e outros objetos no banco de dados. Eles também são usados para realizar atualizações nas estruturas existentes.

#### Cuidados
1) Alterações em tabelas com alto volume de transações e que suportam processos críticos devem ser feitas com extrema cautela. Mudanças nesses cenários podem afetar a performance do sistema, interrompendo ou até travando o ambiente de produção dos clientes.
2) Evite manipular o banco de dados diretamente durante o desenvolvimento, pois isso pode gerar inconsistências e dificuldades futuras. Sempre prefira executar a tarefa "deployAddon" no Gradle para garantir que as alterações sejam aplicadas corretamente.
3) Testes são fundamentais: Realize sempre testes de instalação limpa e de atualizações para garantir que as migrações funcionem como esperado sem causar impactos indesejados.
4) Evite conflitos com outros projetos no banco de dados. Certifique-se de que as tabelas e campos que você cria não interfiram em outras implementações.
5) Use prefixos exclusivos para todos os objetos de banco de dados (como tabelas e campos). Isso ajuda a evitar conflitos e a manter a organização, especialmente em projetos que compartilham o mesmo banco de dados.

Para seguir com a configuração dos scripts de banco, siga a documentação: [Sankhya Developer - Schema de Banco de Dados (DBScripts)](https://developer.sankhya.com.br/docs/02_scripts).

# 5. Dicionário de Dados

Para cada tabela, view ou script que você desejar criar ou alterar, crie um arquivo XML separado. Isso garante que a documentação e o recurso de autocompletar funcionem corretamente durante o desenvolvimento

- Validação no VS Code.
  - Para garantir que o arquivo XML seja validado corretamente no VS Code, é recomendada a instalação do plugin [XML](https://marketplace.visualstudio.com/items?itemName=redhat.vscode-xml) da Red Hat. Esse plugin auxilia na verificação de erros e melhora a experiência de edição do XML, oferecendo recursos como autocompletar e validação de esquemas.

Para seguir com a configuração do dicionário de dados, siga a documentação: [Sankhya Developer - Dicionário de Dados](https://developer.sankhya.com.br/docs/dicionario-de-dados).

# 6. Parâmetros
O Gradle cria automaticamente a configuração de parâmetros necessária durante o processo de build. Para criar o arquivo de parâmetros, o Gradle utiliza o group definido no arquivo build.gradle.

Para configurar os seus parâmetros, siga a documentação: [Sankhya Developer - Parâmetros](https://developer.sankhya.com.br/docs/parametros).

# 7. Considerações Importantes

**Impacto**: Atualizações podem causar instabilidade no sistema do cliente se não forem devidamente testadas.

**Repositório**: Para cada addon que será desenvolvido usando o template, é necessário criar um novo repositório Git dedicado.

# 8. Testes em ambiente local
Para realizar o deploy do addon em um ambiente de testes local, com a variável serverFolder configurada corretamente, execute o comando abaixo:<br>

```bash
 ./gradlew clean deployAddon
```

Para mais informações sobre como efetuar os testes em ambiente local, siga a documentação: [Sankhya Developer - Testando e Publicando seu Add-on](https://developer.sankhya.com.br/docs/04_deploy_testes_locais).

# 9. Código de exemplo

Neste projeto existem [exemplos](addon-template-model/src/main/java/br/com/fabricante/addon/exemplos) de como criar Service, JOB, Listener, Callback, Business Rule, Action Button, Repository, Component, JapeEntity e Validation.
- Service: Similar à um endpoint do spring boot, um Service é um serviço que será chamado via HTTP. Veja mais em [A Camada de Serviço `@Service`](https://developer.sankhya.com.br/docs/09_service) 
- Job: Uma rotina que será executada de tempos em tempos. Veja mais em [Jobs Agendados com `@Job`](https://developer.sankhya.com.br/docs/jobs-agendados-com-job)
- Listener: Um Listener ouve aos eventos (inserção, edição, exclusão) de uma entidade. Veja mais em [Listeners: Reagindo a Eventos de Persistência](https://developer.sankhya.com.br/docs/07_listeners)
- Callback: Hook poderoso para interceptar eventos de alto nível no ciclo de vida de documentos comerciais, como a confirmação de uma nota ou o processamento de um faturamento. Veja mais em [Callbacks: Reagindo a Eventos de Documentos](https://developer.sankhya.com.br/docs/08_callback)
- Bussines Rule: Hook ideal para implementar lógicas de negócio durante os eventos de confirmação e faturamento de documentos comerciais, como Pedidos e Notas de Venda. [Regras de Negócio](https://developer.sankhya.com.br/docs/06_business_rules)
- Action Button: Forma moderna e declarativa de criar botões de ação personalizados em telas do Sankhya Om. Ela permite associar uma classe Java, que executa uma lógica de negócio específica, a um botão visível no menu "Ações" da tela. Veja mais em [Botão de Ação](https://developer.sankhya.com.br/docs/05_action_button)
- Repository: É uma abstração poderosa que simplifica drasticamente o acesso a dados. Ele permite que você defina consultas de forma declarativa, sem escrever uma única linha de implementação, e promove um código limpo, seguro e fácil de manter. Veja mais em [Repositórios de Dados](https://developer.sankhya.com.br/docs/repositorio-dados)
- Component: Marca uma classe como um componente genérico gerenciado pelo framework. É o bloco de construção fundamental para lógica de negócio, utilitários, serviços internos e processadores de dados. Veja mais em [Componentes](http://developer.sankhya.com.br/docs/injecao-de-dependencias#component)
- JapeEntity: Entidades (POJOs) que mapeiam as tabelas e views do banco de dados Sankhya. Essas classes são o coração do seu modelo de domínio.
- Validation: É um mecanismo que permite definir e validar regras de negócio diretamente nos seus objetos de dados (DTOs e Entidades) usando anotações. O SDK Sankhya integra este padrão (especificação JSR 303/380), oferecendo validação automática e declarativa. Veja mais em [Validação de Dados com Bean Validation](https://developer.sankhya.com.br/docs/bean-validation)

É muito importante entender que se tratam apenas de *exemplos* e que *não recomendamos* que os mesmos sejam utilizados em produção.

# 10. Chamada ao ExemploController (ExemploServiceSP)

Para fazer uma requisição HTTP ao serviço disponível neste exemplo:
Como o serviço deste exemplo utiliza autenticação, você deverá realizar login primeiro:

```bash
curl --location 'localhost:8080/mge/service.sbr?serviceName=MobileLoginSP.login&outputType=json' \
--header 'Content-Type: application/json' \
--data '{
	"requestBody": {
		"NOMUSU": {
			"$": "SUP"
		},
		"INTERNO": {
			"$": ""
		}
	}
}'
```

Veja que no exemplo acima, `NOMUSU` é SUP e `INTERNO` está vazio, indicando um login com SUP sem senha. O resultado da request acima é algo parecido com:

```json
{
    "serviceName": "MobileLoginSP.login",
    "status": "1",
    "pendingPrinting": "false",
    "transactionId": "DBC7CC7A1B7FB5A41084BA191D573F5B",
    "responseBody": {
        "callID": {
            "$": "B5F2AA7C501F0441267C1670AD45D027"
        },
        "jsessionid": {
            "$": "o4UWw05TD_GpRaxXnzp0wlpb-Z7bvVkiVEaEQP4W"
        },
        "idusu": {
            "$": "MA==\n"
        }
    }
}
```

Veja que há um atributo chamado `jsessionid`, é este que será usado no exemplo abaixo, no valor de `mgeSession`:

```bash
curl --location 'localhost:8080/addon-template/service.sbr?serviceName=ExemploServiceSP.teste&mgeSession=${jsessionid válido}'
```
Se tudo ocorreu bem, você receberá a seguinte resposta:

```json
{
    "serviceName": "ExemploServiceSP.teste",
    "status": "1",
    "pendingPrinting": "false",
    "transactionId": "5A5EE4FD5A0D499711BE2CCCA8D33DFF",
    "responseBody": {
        "mensagem": "Teste Service...",
        "CODPARC": "123456"
    }
}
```
---
## Recomendações
- Feche as conexões abertas com o DB após utiliza-las;
- Utilize os princípios do Clean Code;
- Trate suas exceções;

---
## Referências
- [Padrão de nomenclatura de branches](https://comunidade.sankhya.com.br/t/sankhya-gitflow-padroes-de-nomenclatura-de-branch-para-um-fluxo-de-desenvolvimento-eficiente/7189)
- [Developer Sankhya](https://developer.sankhya.com.br/docs/add-on)
