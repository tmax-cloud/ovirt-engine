package org.ovirt.engine.core.bll.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.ovirt.engine.core.bll.MacPoolManager;
import org.ovirt.engine.core.bll.context.CompensationContext;
import org.ovirt.engine.core.common.AuditLogType;
import org.ovirt.engine.core.common.businessentities.Network;
import org.ovirt.engine.core.common.businessentities.VM;
import org.ovirt.engine.core.common.businessentities.VmNetworkInterface;
import org.ovirt.engine.core.common.errors.VdcBLLException;
import org.ovirt.engine.core.common.errors.VdcBllErrors;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.core.dal.dbbroker.DbFacade;
import org.ovirt.engine.core.dal.dbbroker.auditloghandling.AuditLogDirector;
import org.ovirt.engine.core.dal.dbbroker.auditloghandling.AuditLogableBase;
import org.ovirt.engine.core.dao.VmDAO;
import org.ovirt.engine.core.dao.VmNetworkInterfaceDAO;
import org.ovirt.engine.core.dao.VmNetworkStatisticsDAO;
import org.ovirt.engine.core.utils.log.Log;
import org.ovirt.engine.core.utils.log.LogFactory;
import org.ovirt.engine.core.utils.transaction.TransactionMethod;
import org.ovirt.engine.core.utils.transaction.TransactionSupport;

/**
 * Helper class to use for adding/removing {@link VmNetworkInterface}s.
 */
public class VmInterfaceManager {

    protected Log log = LogFactory.getLog(getClass());
    /**
     * Add a {@link VmNetworkInterface} to the VM, trying to acquire a MAC from the {@link MacPoolManager}.<br>
     * If the MAC is already in use, a warning will be sent to the user.
     *
     * @param iface
     *            The interface to save.
     * @param compensationContext
     *            Used to snapshot the saved entities.
     * @return <code>true</code> if the MAC wasn't used, <code>false</code> if it was.
     */
    public void add(final VmNetworkInterface iface, CompensationContext compensationContext, boolean allocateMac) {
        if (allocateMac) {
            iface.setMacAddress(getMacPoolManager().allocateNewMac());
        } else if (!getMacPoolManager().AddMac(iface.getMacAddress())) {
            auditLogMacInUse(iface);
            log.errorFormat("VmInterfaceManager::Mac {0} is in use.", iface.getMacAddress());
            throw new VdcBLLException(VdcBllErrors.MAC_ADDRESS_IS_IN_USE);
        }

        getVmNetworkInterfaceDAO().save(iface);
        getVmNetworkStatisticsDAO().save(iface.getStatistics());
        compensationContext.snapshotNewEntity(iface);
        compensationContext.snapshotNewEntity(iface.getStatistics());
    }

    // This method is protected for test purposes
    protected void auditLogMacInUse(final VmNetworkInterface iface) {
        TransactionSupport.executeInNewTransaction(new TransactionMethod<Void>() {
            @Override
            public Void runInTransaction() {
                AuditLogableBase logable = new AuditLogableBase();
                logable.AddCustomValue("MACAddr", iface.getMacAddress());
                logable.AddCustomValue("VmName", iface.getVmName());
                log(logable, AuditLogType.MAC_ADDRESS_IS_IN_USE);
                return null;
            }
        });
    }

    /**
     * Remove all {@link VmNetworkInterface}s from the VM, and remove the Mac addresses from {@link MacPoolManager}.
     *
     * @param vmId
     *            The ID of the VM to remove from.
     */
    public void removeAll(Guid vmId) {
        List<VmNetworkInterface> interfaces = getVmNetworkInterfaceDAO().getAllForVm(vmId);
        if (interfaces != null) {
            for (VmNetworkInterface iface : interfaces) {
                getMacPoolManager().freeMac(iface.getMacAddress());
                getVmNetworkInterfaceDAO().remove(iface.getId());
                getVmNetworkStatisticsDAO().remove(iface.getId());
            }
        }
    }

    /**
     * Checks if a Network is in the given list and is a VM Network
     * @param iface
     * @param networksByName
     * @return
     */
    public boolean isValidVmNetwork(VmNetworkInterface iface, Map<String, Network> networksByName) {
        String networkName = iface.getNetworkName();
        return (networksByName.containsKey(networkName) && networksByName.get(networkName).isVmNetwork());
    }

    /**
     * Finds active VMs which uses a network from a given networks list
     *
     * @param vdsId
     *            The host id on which VMs are running
     * @param networks
     *            the networks to check if used
     * @return A list of VM names which uses the networks
     */
    public List<String> findActiveVmsUsingNetworks(Guid vdsId, List<String> networks) {
        List<VM> runningVms = getVmDAO().getAllRunningForVds(vdsId);
        List<String> vmNames = new ArrayList<String>();
        for (VM vm : runningVms) {
            List<VmNetworkInterface> vmInterfaces = getVmNetworkInterfaceDAO().getAllForVm(vm.getId());
            for (VmNetworkInterface vmNic : vmInterfaces) {
                if (networks.contains(vmNic.getNetworkName())) {
                    vmNames.add(vm.getvm_name());
                    break;
                }
            }
        }
        return vmNames;
    }

    /**
     * Log the given loggable & message to the {@link AuditLogDirector}.
     *
     * @param logable
     * @param auditLogType
     */
    protected void log(AuditLogableBase logable, AuditLogType auditLogType) {
        AuditLogDirector.log(logable, auditLogType);
    }

    protected MacPoolManager getMacPoolManager() {
        return MacPoolManager.getInstance();
    }

    protected VmNetworkStatisticsDAO getVmNetworkStatisticsDAO() {
        return DbFacade.getInstance().getVmNetworkStatisticsDao();
    }

    protected VmNetworkInterfaceDAO getVmNetworkInterfaceDAO() {
        return DbFacade.getInstance().getVmNetworkInterfaceDao();
    }

    protected VmDAO getVmDAO() {
        return DbFacade.getInstance().getVmDao();
    }
}
