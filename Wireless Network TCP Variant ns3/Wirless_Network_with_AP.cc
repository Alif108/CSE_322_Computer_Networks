/* -*- Mode:C++; c-file-style:"gnu"; indent-tabs-mode:nil; -*- */
/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation;
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

#include "ns3/core-module.h"
#include "ns3/point-to-point-module.h"
#include "ns3/network-module.h"
#include "ns3/applications-module.h"
#include "ns3/mobility-module.h"
#include "ns3/csma-module.h"
#include "ns3/internet-module.h"
#include "ns3/yans-wifi-helper.h"

#include "ns3/command-line.h"
#include "ns3/config.h"
#include "ns3/string.h"
#include "ns3/log.h"
#include "ns3/ssid.h"
#include "ns3/mobility-helper.h"
#include "ns3/on-off-helper.h"
#include "ns3/yans-wifi-channel.h"
#include "ns3/mobility-model.h"
#include "ns3/packet-sink.h"
#include "ns3/packet-sink-helper.h"
#include "ns3/tcp-westwood.h"
#include "ns3/internet-stack-helper.h"
#include "ns3/ipv4-address-helper.h"
#include "ns3/ipv4-global-routing-helper.h"
#include "ns3/flow-monitor-module.h"

// Default Network Topology
//
//   Wifi 10.1.3.0
//                 AP
//  *    *    *    *
//  |    |    |    |    10.1.1.0
// n3   n2   n1   n0 -------------- n1   n2   n3   n4
//                   point-to-point  |    |    |    |
//                                   *    *    *    *
//                                   AP     
//                                      Wifi 10.1.2.0

using namespace ns3;

NS_LOG_COMPONENT_DEFINE ("ThirdScriptExample");

// ------------------------- Custom Application Class Starts -------------------- //

class MyApp : public Application 
{
public:

  MyApp ();
  virtual ~MyApp();

  void Setup (Ptr<Socket> socket, Address address, uint32_t packetSize, uint32_t nPackets, DataRate dataRate);

private:
  virtual void StartApplication (void);
  virtual void StopApplication (void);

  void ScheduleTx (void);
  void SendPacket (void);

  Ptr<Socket>     m_socket;
  Address         m_peer;
  uint32_t        m_packetSize;
  uint32_t        m_nPackets;
  DataRate        m_dataRate;
  EventId         m_sendEvent;
  bool            m_running;
  uint32_t        m_packetsSent;
};

MyApp::MyApp ()
  : m_socket (0), 
    m_peer (), 
    m_packetSize (0), 
    m_nPackets (0), 
    m_dataRate (0), 
    m_sendEvent (), 
    m_running (false), 
    m_packetsSent (0)
{
}

MyApp::~MyApp()
{
  m_socket = 0;
}

void
MyApp::Setup (Ptr<Socket> socket, Address address, uint32_t packetSize, uint32_t nPackets, DataRate dataRate)
{
  m_socket = socket;
  m_peer = address;
  m_packetSize = packetSize;
  m_nPackets = nPackets;
  m_dataRate = dataRate;
}

void
MyApp::StartApplication (void)
{
  m_running = true;
  m_packetsSent = 0;
  m_socket->Bind ();
  m_socket->Connect (m_peer);
  SendPacket ();
}

void 
MyApp::StopApplication (void)
{
  m_running = false;

  if (m_sendEvent.IsRunning ())
    {
      Simulator::Cancel (m_sendEvent);
    }

  if (m_socket)
    {
      m_socket->Close ();
    }
}

void 
MyApp::SendPacket (void)
{
  Ptr<Packet> packet = Create<Packet> (m_packetSize);
  m_socket->Send (packet);
  // NS_LOG_UNCOND (Simulator::Now ().GetSeconds () << "\t" << "pakcet size:\t"<<m_packetSize);

  if (++m_packetsSent < m_nPackets)
    {
      ScheduleTx ();
    }
}

void 
MyApp::ScheduleTx (void)
{
  if (m_running)
    {
      Time tNext (Seconds (m_packetSize * 8 / static_cast<double> (m_dataRate.GetBitRate ())));
      m_sendEvent = Simulator::Schedule (tNext, &MyApp::SendPacket, this);
    }
}

static void
CwndChange (Ptr<OutputStreamWrapper> stream, uint32_t oldCwnd, uint32_t newCwnd)
{
  NS_LOG_UNCOND (Simulator::Now ().GetSeconds () << "\t" << newCwnd);
  *stream->GetStream () << Simulator::Now ().GetSeconds () << "\t" << oldCwnd << "\t" << newCwnd << std::endl;
}

// static void
// RxReceive (Ptr<const Packet> p)
// {
//   NS_LOG_UNCOND ("RxReceive at " << Simulator::Now ().GetSeconds ());
// }


// static void
// RxDrop (Ptr<const Packet> p)
// {
//   NS_LOG_UNCOND ("RxDrop at " << Simulator::Now ().GetSeconds ());
// }

// ------------------------- Custom Application Class Ends -------------------- //



int 
main (int argc, char *argv[])
{
  bool verbose = true;
  uint32_t nCsma = 3;
  uint32_t nWifi = 3;
  bool tracing = false;

  CommandLine cmd (__FILE__);
  cmd.AddValue ("nCsma", "Number of \"extra\" CSMA nodes/devices", nCsma);
  cmd.AddValue ("nWifi", "Number of wifi STA devices", nWifi);
  cmd.AddValue ("verbose", "Tell echo applications to log if true", verbose);
  cmd.AddValue ("tracing", "Enable pcap tracing", tracing);

  cmd.Parse (argc,argv);

  // The underlying restriction of 18 is due to the grid position
  // allocator's configuration; the grid layout will exceed the
  // bounding box if more than 18 nodes are provided.
  if (nWifi > 18)
    {
      std::cout << "nWifi should be 18 or less; otherwise grid layout exceeds the bounding box" << std::endl;
      return 1;
    }

  if (verbose)
    {
      LogComponentEnable ("UdpEchoClientApplication", LOG_LEVEL_INFO);
      LogComponentEnable ("UdpEchoServerApplication", LOG_LEVEL_INFO);
    }

  Config::SetDefault ("ns3::TcpL4Protocol::SocketType", TypeIdValue (TypeId::LookupByName ("ns3::TcpVegas")));

  NodeContainer p2pNodes;
  p2pNodes.Create (2);

  PointToPointHelper pointToPoint;
  pointToPoint.SetDeviceAttribute ("DataRate", StringValue ("50Mbps"));
  pointToPoint.SetChannelAttribute ("Delay", StringValue ("20ms"));

  NetDeviceContainer p2pDevices;
  p2pDevices = pointToPoint.Install (p2pNodes);

  Ptr<RateErrorModel> em = CreateObject<RateErrorModel> ();
  em->SetAttribute ("ErrorRate", DoubleValue (0.0001));
  p2pDevices.Get (1)->SetAttribute ("ReceiveErrorModel", PointerValue (em));

  em->SetAttribute ("ErrorRate", DoubleValue (0.00001));
  p2pDevices.Get (0)->SetAttribute ("ReceiveErrorModel", PointerValue (em));



  // ---------------- wifi network left (begin) -------------- //

  NodeContainer wifiStaNodesLeft;
  wifiStaNodesLeft.Create (nWifi);
  NodeContainer wifiApNodeLeft = p2pNodes.Get (0);

  YansWifiChannelHelper channel = YansWifiChannelHelper::Default ();
  YansWifiPhyHelper phy;
  phy.SetChannel (channel.Create ());

  WifiHelper wifi;
  wifi.SetRemoteStationManager ("ns3::AarfWifiManager");

  WifiMacHelper mac;
  Ssid ssid = Ssid ("ns-3-ssid");
  mac.SetType ("ns3::StaWifiMac",
               "Ssid", SsidValue (ssid),
               "ActiveProbing", BooleanValue (false));

  NetDeviceContainer staDevicesLeft;
  staDevicesLeft = wifi.Install (phy, mac, wifiStaNodesLeft);

  mac.SetType ("ns3::ApWifiMac",
               "Ssid", SsidValue (ssid));

  NetDeviceContainer apDevicesLeft;
  apDevicesLeft = wifi.Install (phy, mac, wifiApNodeLeft);

  MobilityHelper mobility;

  mobility.SetPositionAllocator ("ns3::GridPositionAllocator",
                                 "MinX", DoubleValue (0.0),
                                 "MinY", DoubleValue (0.0),
                                 "DeltaX", DoubleValue (5.0),
                                 "DeltaY", DoubleValue (10.0),
                                 "GridWidth", UintegerValue (3),
                                 "LayoutType", StringValue ("RowFirst"));

  mobility.SetMobilityModel ("ns3::RandomWalk2dMobilityModel",
                             "Bounds", RectangleValue (Rectangle (-50, 50, -50, 50)));
  mobility.Install (wifiStaNodesLeft);

  mobility.SetMobilityModel ("ns3::ConstantPositionMobilityModel");
  mobility.Install (wifiApNodeLeft);

  InternetStackHelper stack;
  // stack.Install (csmaNodes);
  stack.Install (wifiApNodeLeft);
  stack.Install (wifiStaNodesLeft);

  // ---------------------- wifi network left (end) -------------------- //

  


  // ---------------------- wifi network right (begin) -------------------- //

  NodeContainer wifiStaNodesRight;
  wifiStaNodesRight.Create (nWifi);
  NodeContainer wifiApNodeRight = p2pNodes.Get (1);

  YansWifiChannelHelper channel2 = YansWifiChannelHelper::Default ();
  // YansWifiChannelHelper channel2;
  // channel2.SetPropagationDelay ("ns3::ConstantSpeedPropagationDelayModel");
  // channel2.AddPropagationLoss ("ns3::FriisPropagationLossModel", "Frequency", DoubleValue (5e9));
  YansWifiPhyHelper phy2;
  phy2.SetChannel (channel2.Create ());

  WifiHelper wifi2;
  wifi2.SetRemoteStationManager ("ns3::AarfWifiManager");

  WifiMacHelper mac2;
  Ssid ssid2 = Ssid ("ns-3-ssid");
  
  mac2.SetType ("ns3::StaWifiMac",
               "Ssid", SsidValue (ssid2),
               "ActiveProbing", BooleanValue (false));
  NetDeviceContainer staDevicesRight;
  staDevicesRight = wifi2.Install (phy2, mac2, wifiStaNodesRight);
  
  mac2.SetType ("ns3::ApWifiMac",
               "Ssid", SsidValue (ssid2));
  NetDeviceContainer apDevicesRight;
  apDevicesRight = wifi2.Install (phy2, mac2, wifiApNodeRight);



  mobility.SetMobilityModel ("ns3::RandomWalk2dMobilityModel",
                             "Bounds", RectangleValue (Rectangle (-50, 50, -50, 50)));
  mobility.Install (wifiStaNodesRight);

  mobility.SetMobilityModel ("ns3::ConstantPositionMobilityModel");
  mobility.Install (wifiApNodeRight);



  InternetStackHelper stack2;
  // stack2.Install (csmaNodes);
  stack2.Install (wifiApNodeRight);
  stack2.Install (wifiStaNodesRight);

  // -------------------- wifi network right (end) -------------------- //



  // ------------- IP ----------- //

  Ipv4AddressHelper address;

  address.SetBase ("10.1.1.0", "255.255.255.0");
  Ipv4InterfaceContainer p2pInterfaces;
  p2pInterfaces = address.Assign (p2pDevices);

  address.SetBase ("10.1.2.0", "255.255.255.0");
  Ipv4InterfaceContainer staInterfacesRight;
  Ipv4InterfaceContainer apInterfacesRight;
  staInterfacesRight = address.Assign (staDevicesRight);
  apInterfacesRight = address.Assign (apDevicesRight);
  // Ipv4InterfaceContainer csmaInterfaces;
  // csmaInterfaces = address.Assign (csmaDevices);

  address.SetBase ("10.1.3.0", "255.255.255.0");
  Ipv4InterfaceContainer staInterfacesLeft;
  Ipv4InterfaceContainer apInterfacesLeft;
  staInterfacesLeft = address.Assign (staDevicesLeft);
  apInterfacesLeft = address.Assign (apDevicesLeft);



  // ---------- sink ------------ //

  Ptr<Node> sinkNode = wifiStaNodesRight.Get(nWifi-1);
  uint16_t sinkPort = 9;
  Address sinkAddress, anyAddress;
  sinkAddress = InetSocketAddress (staInterfacesRight.GetAddress (nWifi-1), sinkPort);
  anyAddress = InetSocketAddress (Ipv4Address::GetAny (), sinkPort);

  PacketSinkHelper sinkHelper ("ns3::TcpSocketFactory", anyAddress);
  ApplicationContainer sinkApp = sinkHelper.Install (sinkNode);
  sinkApp.Start (Seconds (1.));
  sinkApp.Stop (Seconds (50.));



  // ------------ source ---------- //

  Ptr<Node> srcNode = wifiStaNodesLeft.Get(nWifi-1);
  Ptr<MyApp> app = CreateObject<MyApp> ();
  Ptr<Socket> ns3TcpSocket = Socket::CreateSocket (srcNode, TcpSocketFactory::GetTypeId ());
  app->Setup (ns3TcpSocket, sinkAddress, 1200, 200, DataRate ("100Mbps"));
  srcNode->AddApplication(app);
  app->SetStartTime (Seconds (2.));
  app->SetStopTime (Seconds (50.));

  AsciiTraceHelper asciiTraceHelper;
  Ptr<OutputStreamWrapper> stream = asciiTraceHelper.CreateFileStream ("my_network2.cwnd");
  ns3TcpSocket->TraceConnectWithoutContext ("CongestionWindow", MakeBoundCallback (&CwndChange, stream));

  Ipv4GlobalRoutingHelper::PopulateRoutingTables ();

  Simulator::Stop (Seconds (50.0));

  // if (tracing)
  //   {
  //     phy.SetPcapDataLinkType (WifiPhyHelper::DLT_IEEE802_11_RADIO);
  //     pointToPoint.EnablePcapAll ("p2p");
  //     phy.EnablePcap ("wireless", apDevicesLeft.Get (0));
  //     // csma.EnablePcap ("csma", csmaDevices.Get (0), true);
  //   }


  // ----- generating trace files ------- //
  // AsciiTraceHelper ascii;
  // phy.EnableAsciiAll(ascii.CreateFileStream("phy.tr"));
  // csma.EnableAsciiAll(ascii.CreateFileStream("csma.tr"));
  // pointToPoint.EnableAsciiAll(ascii.CreateFileStream("p2p.tr"));

  // Simulator::Run ();

  FlowMonitorHelper flowmon;
  Ptr<FlowMonitor> monitor = flowmon.InstallAll();
  Simulator::Run ();
  monitor->CheckForLostPackets ();
  Ptr<Ipv4FlowClassifier> classifier = DynamicCast<Ipv4FlowClassifier> (flowmon.GetClassifier ());
      std::map<FlowId, FlowMonitor::FlowStats> stats = monitor->GetFlowStats ();

          for (std::map<FlowId, FlowMonitor::FlowStats>::const_iterator i = stats.begin (); i != stats.end (); ++i)
           {
             Ipv4FlowClassifier::FiveTuple t = classifier->FindFlow (i->first);
             /*
              * Calculate the throughtput and other network parameters
              * between TCP source/client (10.1.3.2) and
              * TCP destination/server (10.1.2.2).
              */
               // if ((t.sourceAddress=="10.1.2.1" && t.destinationAddress == "10.1.2.2"))
               // {
                    std::cout << "Flow " << i->first  << " (" << t.sourceAddress << " -> " << t.destinationAddress << ")\n";
                    std::cout << "Sent Packets: " << i->second.txPackets << "\n";
                    std::cout << "Received Packets: " << i->second.rxPackets << "\n";
                    std::cout << "Tx Bytes: " << i->second.txBytes << "\n";
                    std::cout << "Rx Bytes: " << i->second.rxBytes << "\n";
                    std::cout << "First Time: "<<i->second.timeFirstRxPacket.GetSeconds()<<"\n";
                    std::cout << "Last Time: "<<i->second.timeLastTxPacket.GetSeconds()<<"\n";
                    std::cout << "Throughput: " << i->second.rxBytes * 8.0 / (i->second.timeLastRxPacket.GetSeconds() - i->second.timeFirstTxPacket.GetSeconds())/1024/1024  << " Mbps\n";
                    std::cout << "\n";
                // }
           }
  Simulator::Destroy ();
  return 0;
}
