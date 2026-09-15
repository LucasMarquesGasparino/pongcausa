# Pong Causalidade (experimento multiplayer)

Versão **multiplayer por rede local** do experimento da monografia MAC0499:
dois celulares jogam Pong entre si via UDP na mesma Wi-Fi, com coleta de
sensores nos dois aparelhos e análise de causalidade.

- Sem pacote fixo no manifest amostrado (build aapt2+javac+d8 via `build.sh`).
- Protocolo simples na porta UDP **47474**: `paddle|pos|t`, `score|s1|s2`,
  `hello`, `role|A|B`, `event|…`.

## Modos

- **CRIAR PARTIDA (celular 1)**: abre a sala e mostra o IP local.
- **ENTRAR NA PARTIDA (celular 2)**: digita o IP do celular 1.
- **JOGAR AGORA (1 celular)**: contra BOT ou "espelho humano" (humano
  simulado) — modo cego, igual ao `monografia-apk`.

## Sensores catalogados (`SensorCatalog`)

Acelerômetro, giroscópio, magnetômetro, frequência cardíaca (se houver),
contador de passos, proximidade e luz — conforme o hardware de cada aparelho.

## Análise

- `src/`: `MainActivity`, `NetLink` (UDP), `GameLog` (sessões `exp_*`,
  batidas), `SensorCatalog`.
- `analysis/`: scripts da análise estatística (Granger entre sensores e
  posições/resultados).
- `apk/`: APKs de referência (`pong-causalidade.apk`).

## Compilar

```sh
cd ~/pongcausa
bash build.sh
```

## Estrutura

```
pongcausa/
├── src/com/pongcausa/  # MainActivity, NetLink, GameLog, SensorCatalog
├── analysis/            # análise de causalidade
├── apk/                 # APKs de referência
└── res/
```

> Para o experimento cego de 1 aparelho com relatório em HTML, ver
> `monografia-apk`.
