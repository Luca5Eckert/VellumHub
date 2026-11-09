#!/usr/bin/env python3
"""Print operator commands; never executes writes. Prerequisite: freeze/drain checks."""
import argparse
p=argparse.ArgumentParser()
p.add_argument("stage", choices=["prepare","consumers","producer","rollback"])
s=p.parse_args().stage
cmd="kubectl -n vellumhub set env deployment/"
url="http://schema-registry:8080/apis/ccompat/v7"
if s=="prepare":
    print(cmd+"catalog-service BOOK_EVENTS_WIRE_FORMAT=json BOOK_AVRO_REGISTRY_URL="+url)
    for svc in ("recommendation-service","engagement-service"):
        print(cmd+svc+" BOOK_AVRO_CONSUMER_ENABLED=false BOOK_AVRO_REGISTRY_URL="+url)
elif s=="consumers":
    for svc in ("recommendation-service","engagement-service"):
        print(cmd+svc+" BOOK_AVRO_CONSUMER_ENABLED=true")
elif s=="producer":
    print(cmd+"catalog-service BOOK_EVENTS_WIRE_FORMAT=avro")
else:
    print(cmd+"catalog-service BOOK_EVENTS_WIRE_FORMAT=json")
