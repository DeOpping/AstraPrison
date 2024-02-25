package dev.paracausal.astra.utilities.items.pdc.impl;

import dev.paracausal.astra.utilities.items.pdc.ItemPDC;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

public class ItemPDCByteArray extends ItemPDC<byte[]> {

    public ItemPDCByteArray(@NotNull final NamespacedKey key, final byte[] value) {
        super(key, value);
    }

    @Override
    public void modify(@NotNull PersistentDataContainer container) {
        container.set(key, PersistentDataType.BYTE_ARRAY, value);
    }

}
