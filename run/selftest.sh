#!/bin/bash
# Espera o servidor de desenvolvimento subir e manda comandos de teste pelo console.
# Uso: bash run/selftest.sh run/test-output.log | ./gradlew runServer
LOG="$1"
for i in $(seq 1 900); do
  if grep -q 'Done (' "$LOG" 2>/dev/null; then break; fi
  sleep 1
done
sleep 5
send() { echo "$1"; sleep "${2:-3}"; }
send "corrida diagnostico" 5
send "corrida pista criar teste"
send "corrida linha teste definir 0 -61 0 10 -61 0"
send "corrida checkpoint teste adicionar 0 -61 30 10 -61 30"
send "corrida checkpoint teste adicionar 0 -61 60 10 -61 60"
send "corrida voltas teste 2"
send "corrida pista cor teste 3FA9F5"
send "corrida pista lista"
send "execute positioned 5 -57 -3 rotated 180 0 run corrida placar teste" 4
send "execute if entity @e[type=minecraft:text_display,tag=shifurace_placar]"
send "data get entity @e[type=minecraft:text_display,tag=shifurace_placar,limit=1] text"
send "corrida abrir teste 3"
send "corrida iniciar teste"
send "corrida encerrar teste"
send "corrida checkpoint teste remover 2"
send "corrida pista lista"
send "stop" 1
