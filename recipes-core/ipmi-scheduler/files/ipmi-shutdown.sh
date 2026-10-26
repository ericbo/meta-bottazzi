#!/bin/bash
source /etc/ipmi.conf
ipmitool -I lanplus -H ${IPMI_HOST} -U ${IPMI_USER} -P ${IPMI_PASS} power soft
