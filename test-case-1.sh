#!/usr/bin/env bash
curl -X PUT "http://localhost:10007/api/cash/self-issue-cash?amount=500&currency=SGD"
curl -X PUT "http://localhost:10010/api/cash/self-issue-cash?amount=500&currency=SGD"
curl -X PUT "http://localhost:10013/api/cash/self-issue-cash?amount=500&currency=SGD"
curl -X PUT "http://localhost:10016/api/cash/self-issue-cash?amount=500&currency=SGD"

curl -X PUT "http://localhost:10007/api/obligation/issue-obligation?amount=100&currency=SGD&counterparty=NodeB"
curl -X PUT "http://localhost:10010/api/obligation/issue-obligation?amount=200&currency=SGD&counterparty=NodeC"
curl -X PUT "http://localhost:10013/api/obligation/issue-obligation?amount=150&currency=SGD&counterparty=NodeD"
curl -X PUT "http://localhost:10016/api/obligation/issue-obligation?amount=300&currency=SGD&counterparty=NodeA"
curl -X PUT "http://localhost:10019/api/obligation/issue-obligation?amount=700&currency=SGD&counterparty=NodeD"
curl -X PUT "http://localhost:10022/api/obligation/issue-obligation?amount=325&currency=SGD&counterparty=NodeE"
curl -X PUT "http://localhost:10010/api/obligation/issue-obligation?amount=200&currency=SGD&counterparty=NodeF"
curl -X PUT "http://localhost:10025/api/obligation/issue-obligation?amount=200&currency=SGD&counterparty=NodeD"