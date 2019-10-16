# Test script. Tests simulation time commands.
# Created by Toni Sagrista

from py4j.clientserver import ClientServer, JavaParameters

gateway = ClientServer(java_parameters=JavaParameters(auto_convert=True))
gs = gateway.entry_point

gs.disableInput()
gs.cameraStop()

# Jan 1st, 2017  14:23:58:000
gs.setSimulationTime(2017, 1, 1, 14, 23, 58, 0)


gs.enableInput()

gateway.shutdown()
