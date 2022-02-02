from xml.etree import ElementTree as ET
import sys
import matplotlib.pyplot as plt

et=ET.parse(sys.argv[1])

flow_id = []
lost_packets = []
delays = []
delivery_ratio = []
packet_loss_ratio = []

for flow in et.findall("FlowStats/Flow"):
	
	for tpl in et.findall("Ipv4FlowClassifier/Flow"):
		if tpl.get('flowId')==flow.get('flowId'):
			break
	if tpl.get('destinationPort')=='654':
		continue

	sent_packets = int(flow.get('txPackets'))
	received_packets = int(flow.get('rxPackets'))
	received_delay_total = float(flow.get('delaySum')[:-2])*1e-9

	flow_id.append(flow.get('flowId'))
	lost_packets.append(sent_packets - received_packets)
	delays.append(float(received_delay_total / (received_packets + 0.00001)) * 1000)	# 0.00001 is added to avoid division by zero 
	delivery_ratio.append(float(received_packets / sent_packets) * 100)
	packet_loss_ratio.append(float((sent_packets - received_packets) / (sent_packets)) * 100)

fig, axs = plt.subplots(2, 2, figsize=(10, 7))

axs[0, 0].plot(flow_id, lost_packets, color='green', marker='o', linestyle='dashed', linewidth=2, markersize=12)
axs[0, 0].grid()
axs[0, 0].set_title('Lost Packets vs Flow ID')
axs[0, 0].set(xlabel="Flow ID", ylabel="Lost Packets")

axs[0, 1].plot(flow_id, delivery_ratio, color='red', marker='o', linestyle='dashed', linewidth=2, markersize=12)
axs[0, 1].grid()
axs[0, 1].set_title('Delivery Ratio vs Flow ID')
axs[0, 1].set(xlabel="Flow ID", ylabel="Delivery Ratio")

axs[1, 0].plot(flow_id, packet_loss_ratio, color='blue', marker='o', linestyle='dashed', linewidth=2, markersize=12)
axs[1, 0].grid()
axs[1, 0].set_title('Packet Loss Ratio vs Flow ID')
axs[1 ,0].set(xlabel="Flow ID", ylabel="Packet Loss Ratio")

axs[1, 1].plot(flow_id, delays, color='purple', marker='o', linestyle='dashed', linewidth=2, markersize=12)
axs[1, 1].grid()
axs[1, 1].set_title('Delay vs Flow ID')
axs[1, 1].set(xlabel="Flow ID", ylabel="Delay")

plt.subplots_adjust(hspace=0.5)
plt.savefig("stat.pdf")

print("Stat.pdf generated")