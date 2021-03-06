/*******************************************************************************
 * Copyright (c) 2009, 2011 SpringSource, a divison of VMware, Inc. and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     SpringSource, a division of VMware, Inc. - initial API and implementation
 *     SAP AG - moving to Eclipse Libra project and enhancements
 *******************************************************************************/
package org.eclipse.libra.framework.editor.ui.internal.overview;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.libra.framework.editor.core.IOSGiFrameworkAdmin;
import org.eclipse.libra.framework.editor.core.model.IBundle;
import org.eclipse.libra.framework.editor.ui.internal.EditorUIPlugin;
import org.eclipse.pde.internal.ui.PDEPluginImages;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.ui.forms.SectionPart;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;
import org.eclipse.ui.internal.browser.ImageResource;
import org.eclipse.ui.internal.ide.StringMatcher;


/**
 * @author Christian Dupuis
 * @author Steffen Pingel
 * @author Kaloyan Raev
 */
@SuppressWarnings("restriction")
public class BundleInformationMasterPart extends SectionPart {

	private static final String TYPE_FILTER_TEXT = "type filter text";

	private final FormToolkit toolkit;

	private StructuredViewer bundleTableViewer;

	private Table bundleTable;

	private Button startButton;

	private Button stopButton;

	private Button refreshButton;

	private Button updateButton;

	private Text filterText;

	private TableColumn idColumn;

	private TableColumn symbolicNameColumn;

	private TableColumn statusColumn;

	private final BundleInformationMasterDetailsBlock masterDetailsBlock;

	public BundleInformationMasterPart(Composite parent, FormToolkit toolkit, int style,
			BundleInformationMasterDetailsBlock masterDetailsBlock) {
		super(parent, toolkit, style);
		this.toolkit = toolkit;
		this.masterDetailsBlock = masterDetailsBlock;
	}

	protected void createContents() {
		Section section = getSection();
		section.setText("Bundle Status");
		section.setDescription("Information about installed bundles on server.");
		section.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));
		createSectionToolbar(section, toolkit);

		Composite composite = toolkit.createComposite(section);
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		composite.setLayout(layout);
		composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));
		toolkit.paintBordersFor(composite);
		section.setClient(composite);

		filterText = toolkit.createText(composite, TYPE_FILTER_TEXT, SWT.SEARCH | SWT.CANCEL);
		filterText.addModifyListener(new ModifyListener() {

			public void modifyText(ModifyEvent e) {
				bundleTableViewer.refresh();
			}
		});
		GridData data = new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING);
		filterText.setLayoutData(data);

		toolkit.createLabel(composite, "", SWT.NONE);

		bundleTable = toolkit.createTable(composite, SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);
		data = new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING);
		data.heightHint = 550;
		bundleTable.setLayoutData(data);
		bundleTable.setLinesVisible(true);
		TableColumn imageColumn = new TableColumn(bundleTable, SWT.LEFT);
		imageColumn.setWidth(23);
		idColumn = new TableColumn(bundleTable, SWT.RIGHT);
		idColumn.setText("Id");
		idColumn.setWidth(50);
		idColumn.addListener(SWT.Selection, new SortingListener());
		symbolicNameColumn = new TableColumn(bundleTable, SWT.LEFT);
		symbolicNameColumn.setText("Symbolic-Name");
		symbolicNameColumn.setWidth(280);
		symbolicNameColumn.addListener(SWT.Selection, new SortingListener());
		statusColumn = new TableColumn(bundleTable, SWT.CENTER);
		statusColumn.setText("Status");
		statusColumn.setWidth(70);
		statusColumn.addListener(SWT.Selection, new SortingListener());

		bundleTableViewer = new TableViewer(bundleTable);
		bundleTableViewer.setContentProvider(new BundleStatusContentProvider());
		bundleTableViewer.setLabelProvider(new BundleStatusLabelProvider());

		bundleTableViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				getManagedForm().fireSelectionChanged(BundleInformationMasterPart.this, event.getSelection());
			}
		});
		bundleTableViewer.addFilter(new ViewerFilter() {

			@Override
			public boolean select(Viewer viewer, Object parentElement, Object element) {
				if (element instanceof IBundle && filterText.getText().length() > 0
						&& !TYPE_FILTER_TEXT.equals(filterText.getText())) {
					StringMatcher matcher = new StringMatcher(filterText.getText() + "*", true, false);
					return (matcher.match(((IBundle) element).getSymbolicName()));
				}
				return true;
			}
		});
		bundleTable.setHeaderVisible(true);
		bundleTableViewer.setComparator(new BundleSorter(SORT_COLUMN.ID, SWT.UP));

		Composite buttonComposite = new Composite(composite, SWT.NONE);
		buttonComposite.setLayout(new GridLayout(1, true));
		data = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
		buttonComposite.setLayoutData(data);

		startButton = toolkit.createButton(buttonComposite, "Start", SWT.PUSH);
		data = new GridData(GridData.FILL_HORIZONTAL);
		data.widthHint = 70;
		startButton.setLayoutData(data);
		startButton.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				executeServerCommand("start");
			}

		});

		stopButton = toolkit.createButton(buttonComposite, "Stop", SWT.PUSH);
		stopButton.setLayoutData(data);
		stopButton.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				executeServerCommand("stop");
			}

		});

		refreshButton = toolkit.createButton(buttonComposite, "Refresh", SWT.PUSH);
		refreshButton.setLayoutData(data);
		refreshButton.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				executeServerCommand("refresh");
			}

		});

		updateButton = toolkit.createButton(buttonComposite, "Update", SWT.PUSH);
		updateButton.setLayoutData(data);
		updateButton.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				executeServerCommand("update");
			}

		});

		bundleTableViewer.addPostSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				updateButtonState();
			}
		});
		updateButtonState();
	}

	void updateButtonState() {
		boolean bundleSelected = getSelectedBundle() != null;
		startButton.setEnabled(bundleSelected);
		stopButton.setEnabled(bundleSelected);
		refreshButton.setEnabled(bundleSelected);
		updateButton.setEnabled(bundleSelected);
	}
	
	private IBundle getSelectedBundle() {
		Object selectedObject = ((IStructuredSelection) bundleTableViewer
				.getSelection()).getFirstElement();
		if (selectedObject instanceof IBundle) {
			return (IBundle) selectedObject;
		}
		return null;
	}

	private void executeServerCommand(final String command) {
		IBundle bundle = getSelectedBundle();
		if (bundle != null) {
			final long bundleId = Long.parseLong(bundle.getId());
			Job commandJob = new Job("Execute server command '" + command + "'") {

				@Override
				protected IStatus run(final IProgressMonitor monitor) {
					final IOSGiFrameworkAdmin admin = (IOSGiFrameworkAdmin) masterDetailsBlock.getServer()
							.loadAdapter(IOSGiFrameworkAdmin.class, null);
					try {
						if ("start".equals(command)) {
							admin.startBundle(bundleId);
						} else if ("stop".equals(command)) {
							admin.stopBundle(bundleId);
						} else if ("refresh".equals(command)) {
							admin.refreshBundle(bundleId);
						} else if ("update".equals(command)) {
							admin.updateBundle(bundleId);
						}
	
						Display.getDefault().asyncExec(new Runnable() {
							public void run() {
								try {
									masterDetailsBlock.refresh(admin.getBundles(monitor));
								} catch (CoreException e) {
									EditorUIPlugin.log(e);
								}
							}
						});
	
						return Status.OK_STATUS;
					} catch (CoreException e) {
						return e.getStatus();
					}
				}
			};
			commandJob.setSystem(true);
			commandJob.schedule();
		}
	}

	private void createSectionToolbar(Section section, FormToolkit toolkit) {
		ToolBarManager toolBarManager = new ToolBarManager(SWT.FLAT);
		ToolBar toolbar = toolBarManager.createControl(section);
		final Cursor handCursor = new Cursor(Display.getCurrent(), SWT.CURSOR_HAND);
		toolbar.setCursor(handCursor);
		// Cursor needs to be explicitly disposed
		toolbar.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				if ((handCursor != null) && (handCursor.isDisposed() == false)) {
					handCursor.dispose();
				}
			}
		});

		toolBarManager.add(new Action("Refresh", ImageResource.getImageDescriptor(ImageResource.IMG_ELCL_NAV_REFRESH)) {

			@Override
			public void run() {

				IRunnableWithProgress runnable = new IRunnableWithProgress() {

					public void run(final IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
						monitor.beginTask("Updating bundle status from server", 1);
						Display.getDefault().asyncExec(new Runnable() {
							public void run() {
								masterDetailsBlock.getEditorPage().clearStatus();
								
								IOSGiFrameworkAdmin admin = (IOSGiFrameworkAdmin) masterDetailsBlock.getServer()
										.loadAdapter(IOSGiFrameworkAdmin.class, monitor);
								
								if (admin == null) {
									IStatus status = EditorUIPlugin.newErrorStatus("Bundle Overview editor part is not integrated with the runtime.");
									EditorUIPlugin.log(status);
									masterDetailsBlock.getEditorPage().setStatus(status);
								}
								
								try {
									masterDetailsBlock.refresh(admin.getBundles(monitor));
								} catch (CoreException e) {
									EditorUIPlugin.log(e);
									masterDetailsBlock.getEditorPage().setStatus(e.getStatus());
								}
							}
						});
						monitor.worked(1);
					}
				};

				try {
					IRunnableContext context = new ProgressMonitorDialog(Display.getCurrent().getActiveShell());
					context.run(true, true, runnable);
				}
				catch (InvocationTargetException e1) {
				}
				catch (InterruptedException e2) {
				}

			}

		});

		toolBarManager.update(true);

		section.setTextClient(toolbar);
	}

	public boolean refresh(Map<Long, IBundle> bundles) {
		if (bundles != null && !bundles.equals(bundleTableViewer.getInput())) {
			super.refresh();
			bundleTableViewer.setInput(bundles);
			return true;
		}
		return false;
	}

	public void clear() {
		bundleTableViewer.setInput(null);
	}

	class BundleStatusContentProvider implements IStructuredContentProvider {

		private Map<Long, IBundle> bundles;

		public Object[] getElements(Object inputElement) {
			if (bundles != null) {
				return bundles.values().toArray();
			}
			return new Object[0];
		}

		public void dispose() {
		}

		@SuppressWarnings("unchecked")
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			if (newInput instanceof Map) {
				bundles = (Map<Long, IBundle>) newInput;
			}
			else {
				bundles = null;
			}
		}

	}

	class BundleStatusLabelProvider extends LabelProvider implements ITableLabelProvider {

		public Image getColumnImage(Object element, int columnIndex) {
			switch (columnIndex) {
			case 0:
				return PDEPluginImages.get(PDEPluginImages.OBJ_DESC_BUNDLE);
			default:
				return null;
			}
		}

		public String getColumnText(Object element, int columnIndex) {
			IBundle bundle = (IBundle) element;
			switch (columnIndex) {
			case 1:
				return bundle.getId();
			case 2:
				return bundle.getSymbolicName() + " (" + bundle.getVersion() + ")";
			case 3:
				return bundle.getState();
			default:
				return "";
			}
		}
	}

	class BundleStatusComparator extends ViewerComparator {
		@Override
		public void sort(Viewer viewer, Object[] elements) {
			Arrays.sort(elements, new Comparator<Object>() {

				public int compare(Object o1, Object o2) {
					return Long.valueOf(((IBundle) o1).getId()).compareTo(Long.valueOf(((IBundle) o2).getId()));
				}
			});
		}
	}

	class SortingListener implements Listener {

		public void handleEvent(Event event) {
			// determine new sort column and direction
			TableColumn sortColumn = bundleTable.getSortColumn();
			TableColumn currentColumn = (TableColumn) event.widget;
			int dir = bundleTable.getSortDirection();
			if (sortColumn == currentColumn) {
				dir = dir == SWT.UP ? SWT.DOWN : SWT.UP;
			}
			else {
				bundleTable.setSortColumn(currentColumn);
				dir = SWT.UP;
			}
			// sort the data based on column and direction
			SORT_COLUMN sortIdentifier = null;
			if (currentColumn == idColumn) {
				sortIdentifier = SORT_COLUMN.ID;
			}
			if (currentColumn == statusColumn) {
				sortIdentifier = SORT_COLUMN.STATUS;
			}
			if (currentColumn == symbolicNameColumn) {
				sortIdentifier = SORT_COLUMN.NAME;
			}
			bundleTable.setSortDirection(dir);
			bundleTableViewer.setComparator(new BundleSorter(sortIdentifier, dir));
			bundleTableViewer.refresh();
		}
	}

	class BundleSorter extends ViewerComparator {

		private final SORT_COLUMN sortColumn;

		private final int dir;

		public BundleSorter(SORT_COLUMN sortCoLumn, int dir) {
			this.sortColumn = sortCoLumn;
			this.dir = dir;
		}

		@Override
		public int compare(Viewer viewer, Object e1, Object e2) {
			IBundle b1 = (IBundle) e1;
			IBundle b2 = (IBundle) e2;
			int compare = 0;
			switch (sortColumn) {
			case ID:
				compare = Long.valueOf(b1.getId()).compareTo(Long.valueOf(b2.getId()));
				break;
			case STATUS:
				compare = b1.getState().compareTo(b2.getState());
				break;
			case NAME:
				compare = b1.getSymbolicName().compareTo(b2.getSymbolicName());
				break;
			}
			if (this.dir == SWT.DOWN) {
				compare = compare * -1;
			}
			return compare;
		}
	}

	enum SORT_COLUMN {
		ID, STATUS, NAME
	}

	public void setSelectedBundle(IBundle bundle) {
		this.bundleTableViewer.setSelection(new StructuredSelection(bundle));
		this.bundleTableViewer.reveal(bundle);
	}
}
