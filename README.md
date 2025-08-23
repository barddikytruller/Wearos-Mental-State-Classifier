# **Classificador de Estado Mental com Wear OS e Machine Learning**

## **📝 Descrição do Projeto**

Este projeto explora a viabilidade de classificar estados mentais (como "Relaxado" e "Ansioso") em tempo real, utilizando dados biométricos coletados de um smartwatch (Galaxy Watch 4\) e aplicando um modelo de Machine Learning diretamente no dispositivo Android.

O sistema consiste em dois aplicativos: um para o Wear OS, responsável pela coleta de dados, e um para o celular Android, que recebe os dados, armazena, treina o modelo (offline) e realiza a inferência em tempo real.

## **✨ Funcionalidades Principais**

* **Coleta de Dados no Wear OS:**  
  * Leitura contínua de **Frequência Cardíaca (FC)** e **Saturação de Oxigênio (SpO2)**.  
  * Cálculo de **Variabilidade da Frequência Cardíaca (VFC)** a partir dos Intervalos Inter-Batimentos (IBI), utilizando uma janela deslizante para garantir consistência.  
* **Filtro de Atividade:** O relógio utiliza a API de Reconhecimento de Atividade para enviar dados ao celular apenas quando o utilizador está parado, garantindo a qualidade dos dados para análise de estados de repouso.  
* **Comunicação Otimizada:** A comunicação entre o relógio e o celular é otimizada com *throttling*, limitando o envio de mensagens para garantir estabilidade e responsividade.  
* **Armazenamento e Gestão de Dados:** O aplicativo do celular armazena os dados biométricos rotulados pelo utilizador num banco de dados local (Room) e permite a exportação de todo o dataset para um ficheiro CSV.  
* **Inferência em Tempo Real:** Um modelo de Machine Learning (Rede Neural), treinado com os dados do utilizador, é integrado ao aplicativo do celular via TensorFlow Lite. O app faz previsões em tempo real do estado mental (Relaxado/Ansioso) com base nos dados recebidos do relógio.

## **🏛️ Arquitetura do Projeto**

O projeto é construído com uma arquitetura multi-módulo no Android Studio, separando as responsabilidades:

* **:wearos (Módulo do Relógio):**  
  * **Responsabilidade:** Coleta de dados brutos dos sensores e filtro de atividade.  
  * **Tecnologias:** Samsung Health Sensor SDK, Wear OS Activity Recognition API, Wear OS Data Layer API.  
* **:app (Módulo do Celular):**  
  * **Responsabilidade:** Receber, processar e exibir os dados; armazenar dados rotulados; e realizar a inferência em tempo real com o modelo TFLite.  
  * **Tecnologias:** Wear OS Data Layer API, Room Database, TensorFlow Lite.

Fluxo de Dados:  
Sensores do Relógio \-\> Filtro de Atividade \-\> Wear OS Data Layer \-\> App do Celular \-\> Modelo TFLite \-\> Predição na UI

## **🛠️ Tecnologias Utilizadas**

* **Linguagem:** Kotlin  
* **Plataformas:** Android, Wear OS  
* **UI:** Jetpack Compose  
* **Sensores:** Samsung Health Sensor SDK 1.4.0  
* **Comunicação:** Google Wear OS Data Layer API  
* **Banco de Dados:** Android Room Persistence Library  
* **Machine Learning (Treino):** Python, Pandas, Scikit-learn, TensorFlow (Keras) em Google Colab.  
* **Machine Learning (Inferência):** TensorFlow Lite (TFLite)

## **🚀 Como Executar o Projeto**

1. **Clone o repositório:**  
   https://github.com/barddikytruller/WearOS-Mental-State-Classifier.git

2. **Abra no Android Studio:** Abra o projeto na versão mais recente do Android Studio.  
3. **Configure o Relógio:**  
   * Ative o **Modo de Desenvolvedor** no seu Galaxy Watch.  
   * Ative a **Depuração por Wi-Fi**.  
   * Ative o **Modo de Desenvolvedor** na plataforma Samsung Health do relógio para permitir o acesso aos dados brutos dos sensores.  
4. **Compile e Execute:** Compile e execute os módulos :app no seu celular e :wearos no seu relógio.  
5. **Conceda as Permissões:** O aplicativo irá solicitar permissões para Sensores Corporais e Reconhecimento de Atividade. É crucial concedê-las para o funcionamento correto.

## **🧠 Fluxo de Trabalho de Machine Learning**

1. **Coleta de Dados:** Use os aplicativos para coletar dados, rotulando seus estados como "Relaxado" ou "Ansioso" no celular.  
2. **Exportação:** Use o botão "Exportar para CSV" no app do celular para gerar um dataset.  
3. **Treinamento:** Faça o upload do ficheiro CSV para o notebook Python (.ipynb) no Google Colab. O notebook irá:  
   * Realizar a engenharia de features (cálculo de VFC, SDNN, pNN50).  
   * Treinar um modelo de rede neural.  
   * Converter o modelo treinado para o formato .tflite.  
   * Imprimir os parâmetros de normalização (mean e scale) necessários para o app.  
4. **Integração:**  
   * Coloque o ficheiro mental\_state\_model\_v2.tflite na pasta app/src/main/assets/.  
   * Atualize as constantes SCALER\_MEAN e SCALER\_SCALE no MainActivity.kt com os valores obtidos do notebook.  
5. **Recompile:** Recompile o app do celular para que ele comece a usar o novo modelo treinado.