package org.ovirt.engine.core.bll.validator;

import java.util.List;

import org.ovirt.engine.core.bll.ValidationResult;
import org.ovirt.engine.core.bll.utils.VmDeviceUtils;
import org.ovirt.engine.core.common.FeatureSupported;
import org.ovirt.engine.core.common.businessentities.Disk;
import org.ovirt.engine.core.common.businessentities.Disk.DiskStorageType;
import org.ovirt.engine.core.common.businessentities.DiskInterface;
import org.ovirt.engine.core.common.businessentities.VM;
import org.ovirt.engine.core.common.businessentities.VMStatus;
import org.ovirt.engine.core.common.businessentities.VmDevice;
import org.ovirt.engine.core.common.config.Config;
import org.ovirt.engine.core.common.config.ConfigValues;
import org.ovirt.engine.core.common.errors.VdcBllMessages;
import org.ovirt.engine.core.common.osinfo.OsRepository;
import org.ovirt.engine.core.common.utils.Pair;
import org.ovirt.engine.core.common.utils.SimpleDependecyInjector;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.core.dal.dbbroker.DbFacade;
import org.ovirt.engine.core.dao.VmDAO;

/**
 * A validator for the {@link Disk} class.
 *
 */
public class DiskValidator {

    private Disk disk;

    public DiskValidator(Disk disk) {
        this.disk = disk;
    }

    /**
     * Verifies Virtio-SCSI interface validity.
     */
    public ValidationResult isVirtIoScsiValid(VM vm) {
        if (DiskInterface.VirtIO_SCSI != disk.getDiskInterface()) {
            return ValidationResult.VALID;
        }

        if (disk.getSgio() != null) {
            if (DiskStorageType.IMAGE == disk.getDiskStorageType()) {
                return new ValidationResult(VdcBllMessages.SCSI_GENERIC_IO_IS_NOT_SUPPORTED_FOR_IMAGE_DISK);
            }
        }

        if (vm != null) {
            if (!FeatureSupported.virtIoScsi(vm.getVdsGroupCompatibilityVersion())) {
                return new ValidationResult(VdcBllMessages.VIRTIO_SCSI_INTERFACE_IS_NOT_AVAILABLE_FOR_CLUSTER_LEVEL);
            }

            if (!isVirtioScsiControllerAttached(vm.getId())) {
                return new ValidationResult(VdcBllMessages.CANNOT_PERFORM_ACTION_VIRTIO_SCSI_IS_DISABLED);
            }

            return isOsSupportedForVirtIoScsi(vm);
        }

        return ValidationResult.VALID;
    }

    /**
     * Validates that the OS is supported for Virtio-SCSI interface.
     */
    public ValidationResult isOsSupportedForVirtIoScsi(VM vm) {
        //TODO move this config val to osinfo
        final List<String> unsupportedOSs = Config.<List<String>> getValue(ConfigValues.VirtIoScsiUnsupportedOsList);
        String vmOs = SimpleDependecyInjector.getInstance().get(OsRepository.class).getUniqueOsNames().get(vm.getVmOsId());
        for (String os : unsupportedOSs) {
            if (os.equalsIgnoreCase(vmOs)) {
                return new ValidationResult(VdcBllMessages.ACTION_TYPE_FAILED_GUEST_OS_VERSION_IS_NOT_SUPPORTED);
            }
        }
        return ValidationResult.VALID;
    }

    public boolean isVirtioScsiControllerAttached(Guid vmId) {
        return VmDeviceUtils.isVirtioScsiControllerAttached(vmId);
    }

    public ValidationResult isDiskPluggedToVmsThatAreNotDown(boolean checkOnlyVmsSnapshotPluggedTo, List<Pair<VM, VmDevice>> vmsForDisk) {
        if (vmsForDisk == null) {
            vmsForDisk = getVmDAO().getVmsWithPlugInfo(disk.getId());
        }

        for (Pair<VM, VmDevice> pair : vmsForDisk) {
            VmDevice vmDevice = pair.getSecond();

            if (checkOnlyVmsSnapshotPluggedTo && vmDevice.getSnapshotId() == null) {
                continue;
            }

            VM currVm = pair.getFirst();
            if (VMStatus.Down != currVm.getStatus()) {
                if (vmDevice.getIsPlugged()) {
                    return new ValidationResult(VdcBllMessages.ACTION_TYPE_FAILED_VM_IS_NOT_DOWN);
                }
            }
        }

        return ValidationResult.VALID;

    }

    protected VmDAO getVmDAO() {
        return DbFacade.getInstance().getVmDao();
    }
}
