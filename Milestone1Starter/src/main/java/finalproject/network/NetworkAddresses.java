package finalproject.network;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.stream.Collectors;

/** Finds addresses the host can share with other laptops. */
public final class NetworkAddresses {
    private NetworkAddresses() { }

    public static String localAddresses() {
        try {
            String result = Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                    .flatMap(n -> Collections.list(n.getInetAddresses()).stream())
                    .filter(a -> a instanceof Inet4Address && a.isSiteLocalAddress())
                    .map(a -> a.getHostAddress()).distinct().collect(Collectors.joining("\n"));
            return result.isEmpty() ? "No LAN address found; check your network." : result;
        } catch (Exception e) { return "Check your laptop's IPv4 address in network settings."; }
    }

}

